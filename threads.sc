vvv bind pthread
do
    let header = (include "pthread.h")
    do
        let t = header.typedef.pthread_t
        let create = header.extern.pthread_create
        locals;
run-stage;

fn... spawn (closure : (pointer (function voidstar voidstar)), user-arg)
    local thread : pthread.t
    let status =
        pthread.create &thread null closure user-arg

do
    let spawn
    locals;
