/**
 * Copyright (C) 2014 Miroslav Svitok, Dusan Klinec
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef TIMER_ANDROID_H_
#define TIMER_ANDROID_H_

#include <pj/assert.h>
#include <pj/log.h>

PJ_BEGIN_DECL

typedef pj_status_t (*ThreadRegistrator)(char const * thread_name);

PJ_DECL(void) ThreadRegistratorSetter(ThreadRegistrator func);

PJ_END_DECL
#endif /* TIMER_ANDROID_H_ */
