#include <pj/timer.h>
#include <pj/pool.h>
#include <pj/os.h>
#include <pj/string.h>
#include <pj/assert.h>
#include <pj/errno.h>
#include <pj/lock.h>
#include <pj/log.h>


#include <stdlib.h> // for NULL
#include <unwind.h>
#include <dlfcn.h>
#include <string.h> // for memset
#include <pthread.h>

#include "timer_android.h"

#define HEAP_PARENT(X)	(X == 0 ? 0 : (((X) - 1) / 2))
#define HEAP_LEFT(X)	(((X)+(X))+1)


#define DEFAULT_MAX_TIMED_OUT_PER_POLL  (64)

#define THIS_FILE "timer_android.c"


#define MAX_HEAPS 32
#define MAX_ENTRY_PER_HEAP 256
#define REUSE_SLOT_INTERVAL 2

enum
{
    F_DONT_CALL = 1,
    F_DONT_ASSERT = 2,
    F_SET_ID = 4
};

// Forward def of wrapper
int timer_schedule_wrapper(int entry, int entryId, int time);
int timer_cancel_wrapper(int entry, int entryId);


/**
 * The implementation of timer heap.
 */
struct pj_timer_heap_t
{

	unsigned heap_id;

    /** Pool from which the timer heap resize will get the storage from */
    pj_pool_t *pool;

    /** Lock object. */
    pj_lock_t *lock;

    /** Autodelete lock. */
    pj_bool_t auto_delete_lock;

    /** List of known entries. _timer_id helps to find the slot (index is _timer_id -1). _timer_id is also used to compute the android id of the alarm */
    pj_timer_entry* entries[MAX_ENTRY_PER_HEAP];

    /** As we fear android alarm manager does not apply synchronously cancel/enqueue, we do not reuse immediately an entry slot*/
    int entries_reuse_in[MAX_ENTRY_PER_HEAP];

    /** Callback to be called when a timer expires. */
    pj_timer_heap_callback *callback;

};

static volatile ThreadRegistrator thread_registrator = NULL;

// Local thread registration in case of empty callback (emergency).
#define MAX_LOCAL_THREADS 8
static pj_thread_desc local_thread_register[MAX_LOCAL_THREADS];
static unsigned int local_thread_idx = 0;
static pthread_mutex_t local_thread_mutex = PTHREAD_MUTEX_INITIALIZER;

// Timer heap management.
static pj_timer_heap_t* sHeaps[MAX_HEAPS];
static int sCurrentHeap = 0;

/**
 * Helper structure for tracebacks
 */
#define MAX_STACKTRACE 40
typedef struct _cbdata {
    int depth;
    size_t libAddr;
    _Unwind_Ptr region[MAX_STACKTRACE];
    _Unwind_Ptr ip[MAX_STACKTRACE];
} cbdata;

/**
 * Obtains library address in memory.
 * This is needed to get absolute address of function calls from stack trace
 * from absolute memory address. memory address = library address + function address
 */
size_t  get_library_address(const char*  libname)
{
    char path[256];
    char buff[256];
    int len_libname = strlen(libname);
    FILE* file;
    size_t  addr = 0;

    snprintf(path, sizeof path, "/proc/%d/smaps", getpid());
    file = fopen(path, "rt");
    if (file == NULL)
        return 0;

    while (fgets(buff, sizeof buff, file) != NULL) {
        int  len = strlen(buff);
        if (len > 0 && buff[len-1] == '\n') {
            buff[--len] = '\0';
        }
        if (len <= len_libname || memcmp(buff + len - len_libname, libname, len_libname)) {
            continue;
        }
        size_t start, end, offset;
        char flags[4];
        if (sscanf(buff, "%zx-%zx %c%c%c%c %zx", &start, &end,
                   &flags[0], &flags[1], &flags[2], &flags[3], &offset) != 7) {
            continue;
        }
        if (flags[0] != 'r' || flags[2] != 'x') {
            continue;
        }
        addr = start - offset;
        break;
    }
    fclose(file);
    return addr;
}


static _Unwind_Reason_Code trace_callback(struct _Unwind_Context *ctx, void *d)
{
  _Unwind_Reason_Code reason = _URC_NO_REASON;
  int pid = getpid();
  cbdata * tdata = (cbdata*) d;
  //int   depth = tdata->depth;
  char  buf[128];

  _Unwind_Ptr p1 = _Unwind_GetRegionStart(ctx);
  _Unwind_Ptr p2 = _Unwind_GetIP(ctx);

  if (tdata->depth<=10){
  	  tdata->region[tdata->depth] = p1;
  	  tdata->ip[tdata->depth] = p2;
    }

  snprintf(buf, sizeof(buf), "\t#%d:\tsf:%p[0x%x]\tip:%p[0x%x]\n",
            tdata->depth,
            (void*)p1, (void*)p1 - (void*)tdata->libAddr,
            (void*)p2, (void*)p2 - (void*)tdata->libAddr);
  tdata->depth++;

  PJ_LOG(4, (THIS_FILE, "BUFF: pid: %d %s", pid, buf));
  //nw_dbgout( 5, buf, strlen(buf));

  if ( tdata->depth >= 10 )
  {
    reason = _UA_CLEANUP_PHASE;
  }

  return reason;
}

static void ph4backtrace(){
	cbdata tdata;
	tdata.depth = 0;
	size_t libAddr = 0;
	char buff1[2048];
	char buff2[2048];
	char tmp[32];
	int i=0, b1=0, b2=0, t1=0, t2=0;
	int depth;

	libAddr = get_library_address("libsipstackjni.so");
	PJ_LOG(3, (THIS_FILE, "Lib address: 0x%x", libAddr));
	tdata.libAddr = libAddr;
	_Unwind_Backtrace(trace_callback, &tdata);

	depth = tdata.depth > MAX_STACKTRACE ? MAX_STACKTRACE : tdata.depth;

	// print stack pointers sequence
	for(i=0; i<depth; i++){
		snprintf( tmp, 32, "%p ", (void*)(tdata.region[i]-libAddr));
		t1 = strlen(tmp);
		if ((b1+t1) > 2048){
			b1 = 2048;
		} else {
			strcat(buff1, tmp);
		}

		snprintf( tmp, 32, "%p ", (void*)(tdata.ip[i]-libAddr));
		t2 = strlen(tmp);
		if ((b2+t2) > 2048){
			b2 = 2048;
		} else {
			strcat(buff2, tmp);
		}
	}

	PJ_LOG(5, (THIS_FILE, "Traceback address list, IP:     [%s]", buff2));
	PJ_LOG(5, (THIS_FILE, "Traceback address list, region: [%s]", buff1));
}

PJ_INLINE(void) lock_timer_heap( pj_timer_heap_t *ht )
{
    if (ht->lock) {
	pj_lock_acquire(ht->lock);
    }
}

PJ_INLINE(void) unlock_timer_heap( pj_timer_heap_t *ht )
{
    if (ht->lock) {
	pj_lock_release(ht->lock);
    }
}

static int get_entry_id(pj_timer_heap_t *ht, pj_timer_entry *entry){
	return (entry->_timer_id -1) + MAX_ENTRY_PER_HEAP * ht->heap_id;
}

// protected by timer heap lock
static pj_status_t schedule_entry( pj_timer_heap_t *ht,
				   pj_timer_entry *entry, 
				   const pj_time_val *future_time,
				   const pj_time_val *delay) {
	int i = 0;
	int timer_slot = -1;
	// Find one empty slot in ht entries
	for (i = 0; i < MAX_ENTRY_PER_HEAP; i++) {
		if (ht->entries[i] == NULL) {
			if(ht->entries_reuse_in[i] <= 0){
			    ht->entries[i] = entry;
				timer_slot = i;
				break;
	        }else{
	           ht->entries_reuse_in[i] --;
	        }
		}
	}

	if (timer_slot >= 0) {
		// Obtain the next unique sequence number.
		// Set the entry
		entry->_timer_id = timer_slot + 1;
		entry->_timer_value = *future_time;

		pj_uint32_t ft = PJ_TIME_VAL_MSEC(*delay);

		PJ_LOG(5,
				(THIS_FILE, "Scheduling timer %d [%d] of heap[%d] in %ld ms @ %p heap %p; id=%d, cb=%p",
						entry->_timer_id-1,
						ht->heap_id * MAX_ENTRY_PER_HEAP + entry->_timer_id-1,
						ht->heap_id,
						ft,
						entry,
						ht,
						entry->id,
						entry->cb));

		timer_schedule_wrapper((int) entry, get_entry_id(ht, entry), (int) ft);

		return PJ_SUCCESS;
	} else {
		PJ_LOG(2, (THIS_FILE, "Too many timers!"));
		return PJ_ETOOMANY;
	}
}

// Protected by timer heap lock
static int cancel(pj_timer_heap_t *ht, pj_timer_entry *entry, unsigned int flags) {

	PJ_CHECK_STACK();
	//pj_timer_heap_dump(ht);

	// Check to see if the timer_id is out of range.
	// If is true, then cancel() call was too late and timer was either canceled or fired.
	if ( (entry->_timer_id < 0) || (entry->_timer_id >= MAX_ENTRY_PER_HEAP) ) {
		PJ_LOG(4,
				(THIS_FILE, "Ask to cancel something already fired or canceled. entry->_timer_id=%d @ %p heap[%d] @ %p; id=%d, cb=%p",
						entry->_timer_id, entry, ht->heap_id, ht, entry->id, entry->cb));
		return 0;
	} else if (entry->_timer_id == 0){
		PJ_LOG(4,
				(THIS_FILE, "Ask to cancel not inited timer. entry->_timer_id=%d @ %p heap[%d] %p; id=%d, cb=%p",
						entry->_timer_id, entry, ht->heap_id, ht, entry->id, entry->cb));
		return 0;
	}

	PJ_LOG(5, (THIS_FILE, "Cancel timer %d [%d] @ %p of heap[%d] @ %p; id=%d, cb=%p",
			entry->_timer_id-1,
			ht->heap_id * MAX_ENTRY_PER_HEAP + entry->_timer_id-1,
			entry,
			ht->heap_id,
			ht,
			entry->id,
			entry->cb));

	// This includes case where the entry is not linked to the heap anymore = probably fired meanwhile
	if (ht->entries[entry->_timer_id-1] != entry) {
		 PJ_LOG(2,
		       (THIS_FILE, "Cancelling something not linked to this heap : %d : %p vs %p", entry->_timer_id-1, entry, ht->entries[entry->_timer_id-1]));
		if ((flags & F_DONT_ASSERT) == 0){
			PJ_LOG(1, (THIS_FILE, "Assert: Canceling something not linked to this heap : %d : %p vs %p", entry->_timer_id-1, entry, ht->entries[entry->_timer_id-1]));
			//pj_assert(entry == ht->entries[entry->_timer_id]);

			/**
			 * Timer debug - backtrace
			 */
			ph4backtrace();
		} else {
			PJ_LOG(5, (THIS_FILE, "Canceling timer, not linked to heap (not active) timer %d : %p vs %p", entry->_timer_id-1, entry, ht->entries[entry->_timer_id-1]));
		}
		return 0;
	}

	// Note -- due to the fact we rely on android alarm manager, nothing ensure here that cancelCount is actually valid.
	// Previous checks should do the trick to be sure we have actually 1 cancelled timer here.
	int cancelCount = timer_cancel_wrapper((int) entry, get_entry_id(ht, entry));

	if (cancelCount > 0) {
		// Free the slot of this entry in ht
		pj_timer_id_t origTimerId = entry->_timer_id;
		ht->entries[entry->_timer_id - 1] = NULL;
		ht->entries_reuse_in[entry->_timer_id - 1] = REUSE_SLOT_INTERVAL;
		entry->_timer_id = -1;
		PJ_LOG(5, (THIS_FILE, "Canceled timer[%d] @ %p heap[%d] %p. flags=%x. Timer id set to -1; id=%d, cb=%p",
				origTimerId-1, entry, ht->heap_id, ht, flags, entry->id, entry->cb));
	}

	if ((flags & F_DONT_CALL) == 0) {
		// Call the close hook.
		(*ht->callback)(ht, entry);
	}

	return cancelCount;

}


/*
 * Calculate memory size required to create a timer heap.
 */
// fixme !
PJ_DEF(pj_size_t) pj_timer_heap_mem_size(pj_size_t count)
{
    return /* size of the timer heap itself: */
           sizeof(pj_timer_heap_t) + 
           /* size of each entry: */
           (count+2) * (sizeof(pj_timer_entry*)+sizeof(pj_timer_id_t)) +
           /* lock, pool etc: */
           132;
}

/*
 * Create a new timer heap.
 */
PJ_DEF(pj_status_t) pj_timer_heap_create( pj_pool_t *pool,
					  pj_size_t size,
                      pj_timer_heap_t **p_heap)
{
    pj_timer_heap_t *ht;
    pj_size_t i;

    PJ_ASSERT_RETURN(pool && p_heap, PJ_EINVAL);

    *p_heap = NULL;

    // TODO - ensure size is lower than MAX_ENTRY_PER_HEAP

    /* Allocate timer heap data structure from the pool */
    ht = PJ_POOL_ALLOC_T(pool, pj_timer_heap_t);
	if (!ht)
		return PJ_ENOMEM;

	/* Initialize timer heap sizes */
	ht->pool = pool;

	/* Lock. */
	ht->lock = NULL;
	ht->auto_delete_lock = 0;

	// Find one free slot on static table of heaps available
	// -- very basic implementation for now that should be sufficiant for our needs
	for (i = sCurrentHeap; i < MAX_HEAPS; i++) {
		if (sHeaps[i] == NULL) {
			ht->heap_id = i;
			sHeaps[i] = ht;
			sCurrentHeap = i;
			break;
		}
	}

    // RAZ table of pointers to entries for this heap
	pj_bzero(ht->entries, MAX_ENTRY_PER_HEAP * sizeof(pj_timer_entry*));
	pj_bzero(ht->entries_reuse_in, MAX_ENTRY_PER_HEAP * sizeof(int));

	PJ_LOG(3, (THIS_FILE, "Heap initialized %p curHeap=%d", ht, sCurrentHeap));

	*p_heap = ht;
	return PJ_SUCCESS;
}

PJ_DEF(void) pj_timer_heap_destroy( pj_timer_heap_t *ht )
{
	int i;
	lock_timer_heap(ht);
	// Cancel all entries
	for (i = 0; i < MAX_ENTRY_PER_HEAP; i++) {
		if (ht->entries[i] != NULL) {
			pj_timer_entry *entry = ht->entries[i];
			cancel(ht, entry, 1);
		}
	}
	unlock_timer_heap(ht);

	if (ht->lock && ht->auto_delete_lock) {
		pj_lock_destroy(ht->lock);
		ht->lock = NULL;
	}
	sCurrentHeap++;
	sCurrentHeap = sCurrentHeap % MAX_HEAPS;
	sHeaps[ht->heap_id] = NULL;
}

PJ_DEF(void) pj_timer_heap_set_lock(  pj_timer_heap_t *ht,
                                      pj_lock_t *lock,
                                      pj_bool_t auto_del )
{
    if (ht->lock && ht->auto_delete_lock)
        pj_lock_destroy(ht->lock);

    ht->lock = lock;
    ht->auto_delete_lock = auto_del;
}


PJ_DEF(unsigned) pj_timer_heap_set_max_timed_out_per_poll(pj_timer_heap_t *ht,
                                                          unsigned count )
{
    /* Not applicable */
    PJ_UNUSED_ARG(count);
    return MAX_ENTRY_PER_HEAP;
}

PJ_DEF(pj_timer_entry*) pj_timer_entry_init( pj_timer_entry *entry,
                                             int id,
                                             void *user_data,
                                             pj_timer_heap_callback *cb )
{
    pj_assert(entry && cb);

    entry->_timer_id = -1;
    entry->id = id;
    entry->user_data = user_data;
    entry->cb = cb;
    entry->_grp_lock = NULL;

    return entry;
}

PJ_DEF(pj_bool_t) pj_timer_entry_running( pj_timer_entry *entry )
{
    return (entry->_timer_id >= 1);
}

static pj_status_t schedule_w_grp_lock(pj_timer_heap_t *ht,
                                       pj_timer_entry *entry,
                                       const pj_time_val *delay,
                                       pj_bool_t set_id,
                                       int id_val,
                                       pj_grp_lock_t *grp_lock)
{
    pj_status_t status;
    pj_time_val expires;

    PJ_ASSERT_RETURN(ht && entry && delay, PJ_EINVAL);
    PJ_ASSERT_RETURN(entry->cb != NULL, PJ_EINVAL);

    /* Prevent same entry from being scheduled more than once */
    PJ_ASSERT_RETURN(entry->_timer_id < 1, PJ_EINVALIDOP);

    pj_gettickcount(&expires);
    PJ_TIME_VAL_ADD(expires, *delay);
    
    lock_timer_heap(ht);
    status = schedule_entry(ht, entry, &expires, delay);
    if (status == PJ_SUCCESS) {
        if (set_id)
            entry->id = id_val;
        entry->_grp_lock = grp_lock;
        if (entry->_grp_lock) {
            pj_grp_lock_add_ref(entry->_grp_lock);
        }
    }
    unlock_timer_heap(ht);

    return status;
}

PJ_DEF(pj_status_t) pj_timer_heap_schedule( pj_timer_heap_t *ht,
					    pj_timer_entry *entry,
					    const pj_time_val *delay)
{
    return schedule_w_grp_lock(ht, entry, delay, PJ_FALSE, 1, NULL);
}

PJ_DEF(pj_status_t) pj_timer_heap_schedule_w_grp_lock(pj_timer_heap_t *ht,
                                                      pj_timer_entry *entry,
                                                      const pj_time_val *delay,
                                                      int id_val,
                                                      pj_grp_lock_t *grp_lock)
{
    return schedule_w_grp_lock(ht, entry, delay, PJ_TRUE, id_val, grp_lock);
}

static int cancel_timer(pj_timer_heap_t *ht,
            pj_timer_entry *entry,
            unsigned int flags,
            int id_val)
{
    int count;

    PJ_ASSERT_RETURN(ht && entry, PJ_EINVAL);

    lock_timer_heap(ht);
    count = cancel(ht, entry, flags | F_DONT_CALL);
    if ((flags & F_SET_ID) > 0) {
        entry->id = id_val;
     }
     if (entry->_grp_lock) {
        pj_grp_lock_t *grp_lock = entry->_grp_lock;
        entry->_grp_lock = NULL;
        pj_grp_lock_dec_ref(grp_lock);
    }
    unlock_timer_heap(ht);

    return count;
}


PJ_DEF(int) pj_timer_heap_cancel( pj_timer_heap_t *ht,
				  pj_timer_entry *entry)
{
    return cancel_timer(ht, entry, 0, 0);
}

PJ_DEF(int) pj_timer_heap_cancel_if_active(pj_timer_heap_t *ht,
        pj_timer_entry *entry,
        int id_val)
{
    return cancel_timer(ht, entry, F_SET_ID | F_DONT_ASSERT, id_val);
}


PJ_DEF(unsigned) pj_timer_heap_poll( pj_timer_heap_t *ht, 
                                     pj_time_val *next_delay )
{
    /* Polling is not necessary on Android, since all async activities
     * are registered to alarmManager.
     */
    PJ_UNUSED_ARG(ht);
    if (next_delay) {
    	next_delay->sec = 600;
    	next_delay->msec = 0;
    }
    return 0;
}

PJ_DEF(pj_size_t) pj_timer_heap_count( pj_timer_heap_t *ht )
{
    PJ_ASSERT_RETURN(ht, 0);
	unsigned count = 0;
	unsigned i;
	for (i = 0; i < MAX_ENTRY_PER_HEAP; i++) {
		if (ht->entries[i] != NULL) {
			count++;
		}
	}
	return count;
}

PJ_DEF(pj_status_t) pj_timer_heap_earliest_time( pj_timer_heap_t * ht,
					         pj_time_val *timeval)
{
    /* We don't support this! */
    PJ_UNUSED_ARG(ht);

    timeval->sec = 1;
    timeval->msec = 0;

    return PJ_SUCCESS;
}

PJ_DEF(void) ThreadRegistratorSetter(ThreadRegistrator func){
    thread_registrator = func;
}

PJ_BEGIN_DECL
PJ_DEF(pj_status_t) pj_timer_fire(int entry_code_id){
	pj_thread_t *a_thread = NULL;
	unsigned i, j;
	int entry_id, heap_id;
	pj_timer_entry *entry = NULL;

	entry_id = entry_code_id % MAX_ENTRY_PER_HEAP;
	heap_id = entry_code_id / MAX_ENTRY_PER_HEAP;

	// First step is to register the thread if not already done
	if (!pj_thread_is_registered()) {
		char thread_name[256];
		int len = pj_ansi_snprintf(thread_name, sizeof(thread_name), "timer_thread_%d", entry_code_id);
		thread_name[len] = '\0';

		if (thread_registrator != NULL){
		    thread_registrator(thread_name);
		    PJ_LOG(5, (THIS_FILE, "Registered thread [%s] via callback, oldWayNum=%u.", thread_name, local_thread_idx));
		} else {
		    pthread_mutex_lock(&local_thread_mutex);
		    pj_thread_desc * a_thread_desc = &local_thread_register[local_thread_idx];
		    local_thread_idx = (local_thread_idx + 1) % MAX_LOCAL_THREADS;

		    pj_thread_register(thread_name, *a_thread_desc, &a_thread);
		    pthread_mutex_unlock(&local_thread_mutex);

            //pj_assert(1 || "Registration of the thread in an old way...");
        	PJ_LOG(1, (THIS_FILE, "Registered thread [%s] the old way %p (thread_registrator is NULL), idx=%u.",
        	                    thread_name, a_thread_desc, local_thread_idx));
		}
	}

	if(heap_id < 0 || heap_id >= MAX_HEAPS){
        PJ_LOG(1, (THIS_FILE, "Invalid timer code %d", entry_code_id));
        return PJ_EINVAL;
    }

	// Find corresponding ht
	pj_timer_heap_t *ht = sHeaps[heap_id];
	if (ht != NULL) {
	    pj_timer_heap_callback* cb = NULL;
	    pj_grp_lock_t *grp_lock = NULL;

	    PJ_LOG(5, (THIS_FILE, "FIRE timer %d of heap %d @ %p.%p",
	    		entry_id, heap_id, ht->entries[entry_id], ht));

		lock_timer_heap(ht);

		// Get callback if entry valid
		entry = ht->entries[entry_id];

		if (entry == NULL){
			PJ_LOG(3, (THIS_FILE, "FIRE - entry is NULL %d of heap %d; no callback - cancelled?", entry_id, heap_id));
		} else {
			// Sanity checks
			if ((entry->_timer_id-1) != entry_id){
				PJ_LOG(3, (THIS_FILE, "FIRE - entry not NULL %d of heap %d; but inconsistent timer_id=%d", entry_id, heap_id, entry->_timer_id-1));
			} else {
				cb = entry->cb;
			}

			// Store the group lock
			grp_lock = entry->_grp_lock;
			entry->_grp_lock = NULL;
			entry->_timer_id = -1;
		}

		// Release slot - unlink from queue
		ht->entries[entry_id] = NULL;
		ht->entries_reuse_in[entry_id] = REUSE_SLOT_INTERVAL;
		unlock_timer_heap(ht);

        // Callback may modify current entry (re-enqueue), so do not fire cb before release the slot
        if (cb) {
            cb(ht, entry);
        }
        if (grp_lock) {
            pj_grp_lock_dec_ref(grp_lock);
        }
		PJ_LOG(5, (THIS_FILE, "FIRE done and released entry %d of heap %d @ %p", entry_id, heap_id, entry));
	} else {
		PJ_LOG(2, (THIS_FILE, "FIRE Ignore : No heap found at %d for this entry %d", heap_id, entry_code_id));
	}

	return PJ_SUCCESS;
}
PJ_END_DECL
