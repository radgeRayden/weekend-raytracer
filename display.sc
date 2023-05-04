using import struct
using import Array
using import Option
using import glm
using import String

import bottle
using bottle.gpu.types
from (import bottle.src.gpu.binding-interface) let GPUResourceBinding
from (import bottle.src.gpu.bindgroup) let GPUBindGroup

vvv bind shader
""""struct VertexOutput {
        @location(0) texcoords: vec2<f32>,
        @builtin(position) position: vec4<f32>,
    };

    var<private> vertices : array<vec3<f32>, 6u> = array<vec3<f32>, 6u>(
        vec3<f32>(-1.0,  1.0, 0.0), // tl
        vec3<f32>(-1.0, -1.0, 0.0), // bl
        vec3<f32>( 1.0, -1.0, 0.0), // br
        vec3<f32>( 1.0, -1.0, 0.0), // br
        vec3<f32>( 1.0,  1.0, 0.0), // tr
        vec3<f32>(-1.0,  1.0, 0.0), // tl
    );

    var<private> texcoords : array<vec2<f32>, 6u> = array<vec2<f32>, 6u>(
        vec2<f32>(0.0, 1.0),
        vec2<f32>(0.0, 0.0),
        vec2<f32>(1.0, 0.0),
        vec2<f32>(1.0, 0.0),
        vec2<f32>(1.0, 1.0),
        vec2<f32>(0.0, 1.0),
    );

    @group(0)
    @binding(1)
    var s : sampler;

    @group(0)
    @binding(2)
    var t : texture_2d<f32>;

    @vertex
    fn vs_main(@builtin(vertex_index) vindex: u32) -> VertexOutput {
        var out: VertexOutput;
        out.position = vec4<f32>(vertices[vindex], 1.0);
        out.texcoords = texcoords[vindex];
        return out;
    }

    @fragment
    fn fs_main(vertex: VertexOutput) -> @location(0) vec4<f32> {
        return textureSample(t, s, vertex.texcoords);
    }

struct Pixel plain
    r : u8
    g : u8
    b : u8
    a : u8

    inline __+ (lhs rhs)
        static-if (imply? lhs rhs)
            inline (self other)
                this-type
                    self.r + other.r
                    self.g + other.g
                    self.b + other.b
                    self.a + other.a

struct RenderingState
    raytracing-buffer : (Array Pixel)
    raytracing-target : GPUTexture
    pipeline          : GPUPipeline
    bgroup            : GPUBindGroup
    bgroup1           : GPUBindGroup
    width             : u32
    height            : u32

global state : (Option RenderingState)
fn init (width height)
    bottle.gpu.set-clear-color (vec4 1)

    import bottle.src.gpu.common
    let dummies = bottle.src.gpu.common.istate.dummy-resources
    let cache = bottle.src.gpu.common.istate.cached-layouts
    let layout0 =
        try
            'get cache.bind-group-layouts S"StreamingMesh"
        else
            assert false "invalid cache"
    let layout1 =
        try
            'get cache.bind-group-layouts S"Uniforms"
        else
            assert false "invalid cache"

    let target = (GPUTexture none width height)

    state =
        RenderingState
            raytracing-target = target
            pipeline = (GPUPipeline "Basic" (GPUShaderModule shader 'wgsl))
            bgroup =
                GPUBindGroup layout0
                    dummies.buffer
                    dummies.sampler
                    GPUResourceBinding.TextureView target._view
            bgroup1 =
                GPUBindGroup layout1
                    dummies.uniform-buffer
            width = width
            height = height

    renderbuf := ('force-unwrap state) . raytracing-buffer
    'resize renderbuf (width * height)
    ;


fn update (rp data)
    let state = ('force-unwrap state)
    # we need to double buffer because the graphics driver will crash if we send
    # data that we're still touching!
    local copy-color-buffer : (Array vec4)
    'reserve copy-color-buffer (state.width * state.height)
    for el in data
        'append copy-color-buffer el

    tex := state.raytracing-target
    buf := state.raytracing-buffer
    for i in (range (countof data))
        buf @ i =
            typeinit
                va-map
                    (x) -> ((clamp (x * 255) 0. 255.) as u8)
                    unpack (sqrt (copy-color-buffer @ i))

    'write tex buf
    'set-pipeline rp state.pipeline
    'set-bindgroup rp 0:u32 state.bgroup
    'set-bindgroup rp 1:u32 state.bgroup1

    'draw rp 6:u32 1:u32 0:u32 0:u32

do
    let init update
    locals;
