using import radlib.core-extensions

using import Option
using import Array
using import glm
using import struct

import .raydEngine.use
import app
let gfx = (import gfx.webgpu.backend)
let wgpu = (import gfx.webgpu.wrapper)
import HID

FB_WIDTH  := 640:u32
FB_HEIGHT := 480:u32

struct Pixel plain
    r : u8
    g : u8
    b : u8
    a : u8

struct Texture
    id : wgpu.TextureId
    view : wgpu.TextureViewId
    width : u32
    height : u32

    fn update (self data)
        expected-bytes := self.width * self.height * 4
        assert ((Array-sizeof data) == expected-bytes)

        queue := ('force-unwrap gfx.backend) . queue
        wgpu.queue_write_texture queue
            &local wgpu.TextureCopyView
                texture = self.id
                mip_level = 0
                origin = wgpu.Origin3d_ZERO
            (imply data pointer) as (pointer u8)
            (Array-sizeof data)
            &local wgpu.TextureDataLayout
                offset = 0
                bytes_per_row = (self.width * 4)
                rows_per_image = self.height
            &local (wgpu.Extent3d self.width self.height 1)

struct RenderingState
    raytracing-buffer : (Array Pixel (FB_WIDTH * FB_HEIGHT))
    raytracing-target : Texture
    sampler           : wgpu.SamplerId
    pipeline          : wgpu.RenderPipelineId
    # tbinding-layout   : wgpu.BindGroupLayout
    texture-binding   : wgpu.BindGroupId

global state : (Option RenderingState)

fn init ()
    HID.window.set-size FB_WIDTH FB_HEIGHT
    gfx.set-clear-color (vec4 1)

    inline make-sampler (filter-mode)
        let device = (('force-unwrap gfx.backend) . device)
        wgpu.device_create_sampler device
            &local wgpu.SamplerDescriptor
                address_mode_u = wgpu.AddressMode.ClampToEdge
                address_mode_v = wgpu.AddressMode.ClampToEdge
                address_mode_w = wgpu.AddressMode.ClampToEdge
                mag_filter = wgpu.FilterMode.Linear
                min_filter = wgpu.FilterMode.Linear
                mipmap_filter = wgpu.FilterMode.Linear

    fn make-texture (x y format)
        let device = (('force-unwrap gfx.backend) . device)
        let tex =
            wgpu.device_create_texture device
                &local wgpu.TextureDescriptor
                    size = (wgpu.Extent3d x y 1)
                    mip_level_count = 1
                    sample_count = 1
                    dimension = wgpu.TextureDimension.D2
                    format = format
                    usage = (wgpu.TextureUsage_COPY_DST | wgpu.TextureUsage_SAMPLED)
        let tview =
            wgpu.texture_create_view tex
                &local wgpu.TextureViewDescriptor
                    format = format
                    dimension = wgpu.TextureViewDimension.D2
                    aspect = wgpu.TextureAspect.All
                    base_mip_level = 0
                    level_count = 1
                    base_array_layer = 0
                    array_layer_count = 1

        Struct.__typecall Texture
            id = tex
            view = tview
            width = x
            height = y

    inline make-shader (fun stage)
        let device = (('force-unwrap gfx.backend) . device)
        let code = (static-compile-spirv 0x10000 stage (static-typify fun))
        let clen = ((countof code) // 4)

        wgpu.device_create_shader_module device
            &local wgpu.ShaderModuleDescriptor
                code =
                    typeinit
                        bytes = (code as rawstring as (pointer u32))
                        length = clen

    fn make-texture-binding-layout ()
        let device = (('force-unwrap gfx.backend) . device)
        wgpu.device_create_bind_group_layout device
            &local wgpu.BindGroupLayoutDescriptor
                label = "diffuse texture"
                entries =
                    &local
                        arrayof wgpu.BindGroupLayoutEntry
                            typeinit
                                binding = 0
                                visibility = wgpu.ShaderStage_FRAGMENT
                                ty = wgpu.BindingType.SampledTexture
                                view_dimension = wgpu.TextureViewDimension.D2
                                texture_component_type =
                                    wgpu.TextureComponentType.Uint
                            typeinit
                                binding = 1
                                visibility = wgpu.ShaderStage_FRAGMENT
                                ty = wgpu.BindingType.Sampler
                entries_length = 2

    fn make-pipeline (bgroup-layout)
        let device = (('force-unwrap gfx.backend) . device)

        using import glm
        using import glsl
        fn vertex ()
            local vertices =
                arrayof vec2
                    vec2 -1  1 # top left
                    vec2 -1 -1 # bottom left
                    vec2  1 -1 # bottom right
                    vec2  1 -1 # bottom right
                    vec2  1  1 # top right
                    vec2 -1  1 # top left

            local texcoords =
                arrayof vec2
                    vec2 0 1 # top left
                    vec2 0 0 # bottom left
                    vec2 1 0 # bottom right
                    vec2 1 0 # bottom right
                    vec2 1 1 # top right
                    vec2 0 1 # top left

            out vtexcoord : vec2
                location = 0

            gl_Position = (vec4 (vertices @ gl_VertexIndex) 0 1)
            vtexcoord = texcoords @ gl_VertexIndex
        fn fragment ()
            uniform diffuse-t : texture2D
                set = 0
                binding = 0
            uniform diffuse-s : sampler
                set = 0
                binding = 1

            in vtexcoord : vec2
                location = 0
            out fcolor : vec4
                location = 0

            fcolor = (texture (sampler2D diffuse-t diffuse-s) vtexcoord)

        let vshader fshader = (make-shader vertex 'vertex) (make-shader fragment 'fragment)
        let texture-bgroup-layout = (make-texture-binding-layout)
        let pip-layout =
            wgpu.device_create_pipeline_layout device
                &local wgpu.PipelineLayoutDescriptor
                    bind_group_layouts = (&local (imply bgroup-layout u64))
                    bind_group_layouts_length = 1

        wgpu.device_create_render_pipeline device
            &local wgpu.RenderPipelineDescriptor
                layout = pip-layout
                vertex_stage =
                    wgpu.ProgrammableStageDescriptor
                        module = vshader
                        entry_point = "main"
                fragment_stage =
                    &local wgpu.ProgrammableStageDescriptor
                        module = fshader
                        entry_point = "main"
                primitive_topology = wgpu.PrimitiveTopology.TriangleList
                color_states =
                    &local wgpu.ColorStateDescriptor
                        format = wgpu.TextureFormat.Bgra8UnormSrgb
                        alpha_blend =
                            wgpu.BlendDescriptor
                                src_factor = wgpu.BlendFactor.SrcAlpha
                                dst_factor = wgpu.BlendFactor.OneMinusSrcAlpha
                                operation = wgpu.BlendOperation.Add
                        color_blend =
                            wgpu.BlendDescriptor
                                src_factor = wgpu.BlendFactor.SrcAlpha
                                dst_factor = wgpu.BlendFactor.OneMinusSrcAlpha
                                operation = wgpu.BlendOperation.Add
                        write_mask = wgpu.ColorWrite_ALL
                color_states_length = 1
                sample_count = 1
                sample_mask = 0xffffffff

    fn make-texture-bindgroup (layout tview sampler)
        let device = (('force-unwrap gfx.backend) . device)
        wgpu.device_create_bind_group device
            &local wgpu.BindGroupDescriptor
                layout = layout
                entries =
                    &local
                        arrayof wgpu.BindGroupEntry
                            typeinit
                                binding = 0
                                texture_view = tview
                            typeinit
                                binding = 1
                                sampler = sampler
                entries_length = 2

    let tex = (make-texture FB_WIDTH FB_HEIGHT wgpu.TextureFormat.Rgba8UnormSrgb)
    let sampler = (make-sampler wgpu.FilterMode.Linear)
    let bgroup-layout = (make-texture-binding-layout)
    state =
        RenderingState
            raytracing-target = tex
            sampler = sampler
            pipeline = (make-pipeline bgroup-layout)
            texture-binding = (make-texture-bindgroup bgroup-layout tex.view sampler)
    buf := ('force-unwrap state) . raytracing-buffer
    tex := ('force-unwrap state) . raytracing-target
    'resize buf ('capacity buf)
    for p in buf
        p.r = 0xff
        p.g = 0x00
        p.b = 0xff
        p.a = 0xff
    'update tex buf
    ;

struct Ray plain
    origin    : vec3
    direction : vec3

    fn at (self t)
        self.origin + (self.diretion * t)

aspect-ratio    := FB_WIDTH / FB_HEIGHT
viewport-height := 2.0
viewport-width  := aspect-ratio * viewport-height
focal-length    := 1.0

origin            := (vec3)
horizontal        := (vec3 viewport-width 0 0)
vertical          := (vec3 0 viewport-height 0)
lower-left-corner := origin - (horizontal / 2) - (vertical / 2) - (vec3 0 0 focal-length)
run-stage;

fn ray-color (r)
    n := (normalize r.direction)
    t := 0.5 * (n.y + 1)
    mix (vec4 1) (vec4 0.5 0.7 1 1) t
fn color (uv)
    let r =
        Ray origin (lower-left-corner + ((vec3 uv 0) * (horizontal + vertical)) - origin)
    ray-color r

fn update (dt)
    tex := ('force-unwrap state) . raytracing-target
    buf := ('force-unwrap state) . raytracing-buffer
    using import itertools
    using import glm
    for x y in (dim tex.width tex.height)
        uv  := (vec2 x y) / (vec2 FB_WIDTH FB_HEIGHT)
        idx := y * FB_WIDTH + x
        buf @ idx =
            typeinit
                va-map
                    (x) -> ((min (x * 255) 255:f32) as u8)
                    unpack (color uv)

    'update tex buf
    ;

fn draw (cmd-encoder render-pass)
    let state = ('force-unwrap state)
    wgpu.render_pass_set_pipeline render-pass state.pipeline
    wgpu.render_pass_set_bind_group render-pass 0 state.texture-binding null 0
    wgpu.render_pass_draw render-pass 6 1 0 0
    ;

app.run init update draw
