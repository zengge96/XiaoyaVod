#include <jni.h>
#include <dlfcn.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <string.h>
#include <stdio.h>
#include <unistd.h>
#include <stdarg.h>
#include <signal.h>
#include <ucontext.h>
#include <android/log.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "CMDHook64", __VA_ARGS__)

static void flog(const char *fmt, ...) {
    FILE *f;
    va_list ap;
    static const char *paths[] = {
        "/sdcard/TV/ysp_hook.log",
        "/storage/emulated/0/Android/data/com.oktv.box/files/ysp_hook.log",
        "/sdcard/Android/data/com.oktv.box/files/ysp_hook.log",
        "/data/local/tmp/ysp_hook.log"
    };
    int i;
    f = NULL;
    for (i = 0; i < 4; i++) {
        f = fopen(paths[i], "a");
        if (f) break;
    }
    if (!f) return;
    va_start(ap, fmt);
    vfprintf(f, fmt, ap);
    va_end(ap);
    fflush(f);
    fclose(f);
}

static void crash_sig(int sig, siginfo_t *si, void *ctx) {
    FILE *f;
    static const char *cpaths[] = {
        "/sdcard/TV/ysp_crash.log",
        "/storage/emulated/0/Android/data/com.oktv.box/files/ysp_crash.log",
        "/data/local/tmp/ysp_crash.log"
    };
    int i;
    f = NULL;
    for (i = 0; i < 3; i++) {
        f = fopen(cpaths[i], "w");
        if (f) break;
    }
    if (f) {
        ucontext_t *uc = (ucontext_t*)ctx;
        fprintf(f, "SIGNAL=%d SIADDR=%p\n", sig, si ? si->si_addr : 0);
        if (uc) fprintf(f, "PC=%p LR=%p SP=%p\n", (void*)uc->uc_mcontext.pc, (void*)uc->uc_mcontext.regs[30], (void*)uc->uc_mcontext.sp);
        fflush(f);
        fclose(f);
    }
    _exit(128 + sig);
}

typedef ssize_t (*read_fn)(int, void*, size_t);
typedef size_t (*fread_fn)(void*, size_t, size_t, void*);
static read_fn real_read;
static fread_fn real_fread;
static const char *FAKE = "com.cctv.yangshipin.app.androidp";
static char g_realPkg[128] = "com.fongmi.android.tv";
static int g_spoofCnt = 0;

static int is_cmdline_fd(int fd) {
    char path[64], link[160];
    int n;
    snprintf(path, sizeof(path), "/proc/self/fd/%d", fd);
    n = (int)readlink(path, link, sizeof(link)-1);
    if (n < 0) return 0;
    link[n] = '\0';
    return strstr(link, "cmdline") != NULL;
}

static void spoof(void *buf, size_t len) {
    char *p = (char*)buf;
    size_t rlen = strlen(g_realPkg), flen = strlen(FAKE), i;
    for (i = 0; i + rlen <= len; i++) {
        if (memcmp(p + i, g_realPkg, rlen) == 0) {
            if (len >= flen + 1) {
                memcpy(p, FAKE, flen);
                p[flen] = '\0';
                if (g_spoofCnt++ < 20) flog("SPOOF fd pkg=%s off=%u len=%u\n", g_realPkg, (unsigned)i, (unsigned)len);
                LOGI("CMDLINE_SPOOFED %s off=%u", g_realPkg, (unsigned)i);
            }
            return;
        }
    }
}

ssize_t my_read(int fd, void *buf, size_t count) {
    ssize_t n = real_read(fd, buf, count);
    if (n > 0 && is_cmdline_fd(fd)) spoof(buf, (size_t)n);
    return n;
}

size_t my_fread(void *ptr, size_t size, size_t nmemb, void *stream) {
    size_t r = real_fread(ptr, size, nmemb, stream);
    if (r > 0 && stream && is_cmdline_fd(fileno((FILE*)stream))) spoof(ptr, r * size);
    return r;
}

static void* find_base(const char *name) {
    FILE *f = fopen("/proc/self/maps", "r");
    char line[512];
    if (!f) return NULL;
    while (fgets(line, sizeof(line), f)) {
        if (strstr(line, name) && strstr(line, "r-xp")) {
            unsigned long start = 0;
            sscanf(line, "%lx-", &start);
            fclose(f);
            return (void*)start;
        }
    }
    fclose(f);
    return NULL;
}

static void hook_got(void *base, unsigned long off, void *repl) {
    void **got = (void**)((unsigned long)base + off);
    long ps = sysconf(_SC_PAGESIZE);
    mprotect((void*)((unsigned long)got & ~(ps-1)), ps, PROT_READ|PROT_WRITE);
    *got = repl;
    flog("GOT %p -> %p\n", got, repl);
    LOGI("GOT_HOOK %p -> %p", got, repl);
}

JNIEXPORT void JNICALL Java_com_github_catvod_spider_Ysp_installCmdHook(JNIEnv *env, jclass cls, jstring pkg) {
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_sigaction = crash_sig;
    sa.sa_flags = SA_SIGINFO;
    sigaction(SIGSEGV, &sa, NULL);
    sigaction(SIGABRT, &sa, NULL);
    sigaction(SIGILL, &sa, NULL);
    flog("=== install start ===\n");
    if (pkg) {
        const char *s = (*env)->GetStringUTFChars(env, pkg, NULL);
        if (s) {
            strncpy(g_realPkg, s, sizeof(g_realPkg)-1);
            g_realPkg[sizeof(g_realPkg)-1] = '\0';
            (*env)->ReleaseStringUTFChars(env, pkg, s);
            flog("TARGET_PKG=%s\n", g_realPkg);
            LOGI("TARGET_PKG=%s", g_realPkg);
        }
    }
    real_read = (read_fn)dlsym(RTLD_DEFAULT, "read");
    real_fread = (fread_fn)dlsym(RTLD_DEFAULT, "fread");
    if (!real_read || !real_fread) { flog("NO_SYMS\n"); return; }
    void *b1 = find_base("libckeygenerator.so");
    void *b2 = find_base("libckguard.so");
    flog("BASES ck=%p guard=%p\n", b1, b2);
    if (b1) {
        hook_got(b1, 0x5ba10, (void*)my_fread);
        hook_got(b1, 0x5ba38, (void*)my_read);
    }
    if (b2) {
        hook_got(b2, 0x150f28, (void*)my_fread);
        hook_got(b2, 0x151068, (void*)my_read);
    }
    flog("ALL_GOT_HOOKED\n");
    LOGI("ALL_GOT_HOOKED");
}
