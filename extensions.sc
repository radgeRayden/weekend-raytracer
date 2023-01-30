spice _enum-class-constructor (cls v)
    cls as:= type
    T := ('typeof v)
    let fields = ('@ cls '__fields__)
    for ft in ('args fields)
        ft as:= type
        let Type = (('@ ft 'Type) as type)
        if (('element@ Type 0) == T)
            return `([('@ ft '__typecall)] ft v)
    hide-traceback;
    error (.. "type " (tostring cls) " doesn't contain subtype " (tostring T))

run-stage;

inline enum-class-constructor (cls v)
    _enum-class-constructor cls v

inline va-tail (...)
    let __ args = (va-split 1 ...)
    args;

locals;
