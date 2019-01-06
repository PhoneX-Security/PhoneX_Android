//
//  PEXPjZrtpTimer.c
//  Phonex
//
//  Created by Dusan Klinec on 09.12.14.
//  Copyright (c) 2014 PhoneX. All rights reserved.
//
#include <zrtp_timer.h>
#include <pj/config_site.h>
#include <pjmedia/endpoint.h>
#include <pjlib.h>
#include <pjlib-util.h>
#include <inttypes.h>

#ifdef DYNAMIC_TIMER
#include <pj/config_site.h>
#include <pjsua-lib/pjsua.h>
#include <pjsua-lib/pjsua_internal.h>
#else

/**
* The static, singleton Timer implementation
*/
static pj_thread_t* thread_run;
static pj_pool_t* timer_pool;
static pj_timer_heap_t* timer;
static pj_sem_t* timer_sem;
static pj_bool_t timer_running;
static pj_bool_t timer_initialized = 0;
static pj_mutex_t* timer_mutex;

static void timer_stop();
static int timer_thread_run(void *p);
static int timer_initialize();

pj_bool_t zrtp_timer_is_initialized(){
    return timer_pool != NULL;
}

pj_status_t zrtp_timer_init(pjmedia_endpt *endpt){
    pj_status_t rc = PJ_SUCCESS;
    if (timer_pool == NULL) {
        timer_pool = pjmedia_endpt_create_pool(endpt, "zrtp_timer", 256, 256);
        rc = timer_initialize();
        if (rc != PJ_SUCCESS){
            pj_pool_release(timer_pool);
        }
    }

    return rc;
}

static void timer_stop() {
    timer_running = 0;
    pj_sem_post(timer_sem);
}

static int timer_thread_run(void *p) {
    pj_time_val tick = {0, 10};
    timer_running = 1;

    while (timer_running) {
        if (pj_timer_heap_count(timer) == 0) {
            pj_sem_wait(timer_sem);
        }
        else {
            pj_thread_sleep(PJ_TIME_VAL_MSEC(tick));
            pj_timer_heap_poll(timer, NULL);
        }
    }
    pj_timer_heap_destroy(timer);
    timer = NULL;
    pj_sem_destroy(timer_sem);
    timer_sem = NULL;
    pj_pool_release(timer_pool);
    timer_pool = NULL;
    timer_initialized = 0;
    return 0;
}

static int timer_initialize() {
    pj_status_t rc;
    pj_mutex_t *temp_mutex;

    rc = pj_mutex_create_simple(timer_pool, "zrtp_timer", &temp_mutex);
    if (rc != PJ_SUCCESS) {
        return rc;
    }

    pj_enter_critical_section();
    if (timer_mutex == NULL) {
        timer_mutex = temp_mutex;
    } else {
        pj_mutex_destroy(temp_mutex);
    }

    pj_leave_critical_section();
    pj_mutex_lock(timer_mutex);

    if (timer_initialized) {
        pj_mutex_unlock(timer_mutex);
        return PJ_SUCCESS;
    }

    rc = pj_timer_heap_create(timer_pool, 4, &timer);
    if (rc != PJ_SUCCESS) {
        goto ERROR;
    }

    rc = pj_sem_create(timer_pool, "zrtp_timer", 0, 1, &timer_sem);
    if (rc != PJ_SUCCESS) {
        goto ERROR;
    }

    rc = pj_thread_create(timer_pool, "zrtp_timer", &timer_thread_run, NULL,
            PJ_THREAD_DEFAULT_STACK_SIZE, 0, &thread_run);
    if (rc != PJ_SUCCESS) {
        goto ERROR;
    }
    timer_initialized = 1;
    pj_mutex_unlock(timer_mutex);
    return PJ_SUCCESS;

    ERROR:
    if (timer != NULL) {
        pj_timer_heap_destroy(timer);
        timer = NULL;
    }
    if (timer_sem != NULL) {
        pj_sem_destroy(timer_sem);
        timer_sem = NULL;
    }
    if (timer_mutex != NULL) {
        pj_mutex_unlock(timer_mutex);
        pj_mutex_destroy(timer_mutex);
        timer_mutex = NULL;
    }

    return rc;
}

int zrtp_timer_add_entry(pj_timer_entry *entry, pj_time_val *delay) {
    pj_status_t rc;

    if (timer_initialized && timer != NULL) {
        rc = pj_timer_heap_schedule(timer, entry, delay);
        pj_sem_post(timer_sem);
        return rc;
    }
    else {
        return PJ_EIGNORED;
    }
}

int zrtp_timer_cancel_entry(pj_timer_entry *entry) {
    if (timer_initialized && timer != NULL) {
        return pj_timer_heap_cancel(timer, entry);
    } else {
        return PJ_EIGNORED;
    }
}
/*
 * End of timer implementation
 */
#endif
