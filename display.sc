using import struct
using import Array
using import Option
using import glm
using import String

import bottle
using bottle.gpu.types
using bottle.enums
from bottle.asset let ImageData

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
    @binding(0)
    var s : sampler;

    @group(0)
    @binding(1)
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

struct RenderingState
    raytracing-buffer : bottle.asset.ImageData
    raytracing-target : Texture
    pipeline          : RenderPipeline
    bgroup            : BindGroup
    width             : u32
    height            : u32

global state : (Option RenderingState)
fn init (width height)
    bottle.gpu.set-clear-color (vec4 1)

    target := (Texture width height none (ImageData width height))

    vert := ShaderModule (String shader) ShaderLanguage.WGSL ShaderStage.Vertex
    frag := ShaderModule (String shader) ShaderLanguage.WGSL ShaderStage.Fragment

    pipeline :=
        RenderPipeline
            layout = (nullof PipelineLayout)
            topology = PrimitiveTopology.TriangleList
            winding = FrontFace.CCW
            vertex-stage =
                VertexStage
                    module = vert
                    entry-point = "vs_main"
            fragment-stage =
                FragmentStage
                    module = frag
                    entry-point = S"fs_main"
                    color-targets =
                        (Array ColorTarget)
                            typeinit
                                format = (bottle.gpu.get-preferred-surface-format)

    state =
        RenderingState
            raytracing-target = target
            raytracing-buffer = ImageData width height
            pipeline = pipeline
            bgroup =
                BindGroup ('get-bind-group-layout pipeline 0) (Sampler)
                    TextureView target
            width = width
            height = height

    ;


fn update (data)
    let state = ('force-unwrap state)

    tex := state.raytracing-target
    buf := state.raytracing-buffer

    for i pixel in (enumerate data)
        offset := i * 4

        gen :=
            va-zip
                va-each (va-range (countof (typeof pixel)))
                va-each (unpack (sqrt pixel))

        static-fold (result = none) for i x in gen
            buf.data @ (offset + i) = ((clamp (x * 255) 0. 255.) as u8)

    rp := RenderPass (bottle.gpu.get-cmd-encoder) (ColorAttachment (bottle.gpu.get-swapchain-image) (clear? = false))
    'frame-write tex buf
    'set-pipeline rp state.pipeline
    'set-bind-group rp 0:u32 state.bgroup

    'draw rp 6:u32
    'finish rp

do
    let init update
    locals;
