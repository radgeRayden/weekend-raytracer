# quick and (very) dirty wrapper for pthreads
using import libc

fn... spawn (closure : (pointer (function voidstar voidstar)), user-arg)
    local thread : pthread.t
    let status =
        pthread.create &thread null closure user-arg

do
    let spawn
    locals;
