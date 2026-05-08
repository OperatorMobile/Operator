#include <stddef.h>
#include <stdint.h>

#if defined(__ANDROID__) && defined(__aarch64__)

void __clear_cache(void *start, void *end) {
    uintptr_t begin = (uintptr_t)start;
    uintptr_t finish = (uintptr_t)end;

    if (begin >= finish) {
        return;
    }

    uintptr_t ctr_el0;
    __asm__ __volatile__("mrs %0, ctr_el0" : "=r"(ctr_el0));

    size_t dcache_line_size = 4u << ((ctr_el0 >> 16) & 15u);
    uintptr_t cursor = begin & ~(uintptr_t)(dcache_line_size - 1u);
    for (; cursor < finish; cursor += dcache_line_size) {
        __asm__ __volatile__("dc cvau, %0" : : "r"(cursor) : "memory");
    }

    __asm__ __volatile__("dsb ish" : : : "memory");

    size_t icache_line_size = 4u << (ctr_el0 & 15u);
    cursor = begin & ~(uintptr_t)(icache_line_size - 1u);
    for (; cursor < finish; cursor += icache_line_size) {
        __asm__ __volatile__("ic ivau, %0" : : "r"(cursor) : "memory");
    }

    __asm__ __volatile__("dsb ish" : : : "memory");
    __asm__ __volatile__("isb" : : : "memory");
}

#elif defined(__ANDROID__) && defined(__x86_64__)

void __clear_cache(void *start, void *end) {
    (void)start;
    (void)end;
}

#endif
