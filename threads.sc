import sdl

fn... spawn (closure : (pointer (function i32 voidstar)), name, data)
    sdl.CreateThread closure name data

fn get-core-count ()
    sdl.GetCPUCount;

do
    let spawn get-core-count
    locals;
