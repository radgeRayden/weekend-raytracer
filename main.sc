using import Array
using import glm
using import struct

import .radlib.use
import .raydEngine.use
import app
import HID
import PRNG
import timer

using import .utils
using import .materials
using import .hittables
using import .camera
using import .scene
import .threads
import .display

THREAD_COUNT := 12

RT_SAMPLE_COUNT := 500
MAX_BOUNCES     := 50

FB_WIDTH     := 1200:u32
FB_HEIGHT    := 675:u32
vFOV         := 20 * (pi / 180)
aspect-ratio := FB_WIDTH / FB_HEIGHT

lookfrom   := (vec3 13 2 3)
lookat     := (vec3 0 0 0)
vup        := (vec3 0 1 0)
focus-dist := 10.0
aperture   := 0.1
cam        := (Camera lookfrom lookat vup vFOV aspect-ratio aperture focus-dist)
run-stage;

# random scene
global rng : PRNG.random.Xoshiro256** 0
global world : Scene
global ground-material =
    'add-material world
        LambertianM (albedo = (vec3 0.5))
'emplace-append world.objects
    SphereH (vec3 0 -1000 0) 1000 ground-material

for a in (range -11 11)
    for b in (range -11 11)
        a as:= f32
        b as:= f32
        choose-mat := ('normalized rng)
        let center =
            vec3
                a + 0.9 * ('normalized rng)
                0.2
                b + 0.9 * ('normalized rng)

        fn... random-color (lb = 0.0, ub = 1.0)
            vec3
                ('normalized rng) * (ub - lb) + lb
                ('normalized rng) * (ub - lb) + lb
                ('normalized rng) * (ub - lb) + lb

        if ((length (center - (vec3 4 0.2 0))) > 0.9)
            let sphere-material =
                if (choose-mat < 0.8)
                    albedo := (random-color) * (random-color)
                    'add-material world (LambertianM albedo)
                elseif (choose-mat < 0.95)
                    albedo := (random-color 0.5 1.0)
                    roughness := (('normalized rng) * 0.5) as f32
                    'add-material world (MetallicM albedo roughness)
                else
                    'add-material world (DielectricM 1.5)

            'emplace-append world.objects
                SphereH center 0.2 sphere-material

global material1 = ('add-material world (DielectricM 1.5))
'emplace-append world.objects
    SphereH (vec3 0 1 0) 1.0 material1
global material2 = ('add-material world (LambertianM (vec3 0.4 0.2 0.1)))
'emplace-append world.objects
    SphereH (vec3 -4 1 0) 1.0  material2
global material3 = ('add-material world (MetallicM (vec3 0.7 0.6 0.5) 0.0))
'emplace-append world.objects
    SphereH (vec3 4 1 0) 1.0 material3

fn color (uv rng)
    vvv bind ray-color
    loop (r color bounces = ('ray cam uv rng) (vec3 1) 0)
        if (bounces >= MAX_BOUNCES)
            break (vec3)

        let hit? record = ('hit? world.objects r 0.001 Inf)
        if hit?
            mat := ('material world record.mat)
            let scattered? sray attenuation = ('scatter mat r record rng)
            if scattered?
                _ sray (attenuation * color) (bounces + 1)
            else
                # didn't arrive at where we're looking at;
                # remember we're tracing the path in reverse.
                break (vec3)
        else
            n := (normalize r.direction)
            t := 0.5 * (n.y + 1)
            break
                color * (mix (vec3 1) (vec3 0.5 0.7 1) t)
    vec4 ray-color 1

global color-buffer : (Array vec4 (FB_WIDTH * FB_HEIGHT))
'resize color-buffer ('capacity color-buffer)

inline render-pixel (x y rng)
    scale := 1.0 / RT_SAMPLE_COUNT
    vvv bind color-result
    fold (color-result = (vec4)) for i in (range RT_SAMPLE_COUNT)
        let uv =
            /
                (vec2 x y) + (vec2 ('normalized rng) ('normalized rng))
                (vec2 (FB_WIDTH - 1) (FB_HEIGHT - 1))
        color-result + (color uv rng)

    color-result := color-result * scale
    idx := y * FB_WIDTH + x
    color-buffer @ idx = color-result

inline render-pixel-avg (x y samples rng)
    samples as:= f32
    let uv =
        /
            (vec2 x y) + (vec2 ('normalized rng) ('normalized rng))
            (vec2 (FB_WIDTH - 1) (FB_HEIGHT - 1))
    let this-sample = (color uv rng)

    idx := y * FB_WIDTH + x
    accumulated := color-buffer @ idx
    color-result := ((accumulated * samples) + this-sample) / (samples + 1)
    color-buffer @ idx = color-result

fn render-row (row rng)
    for x in (range FB_WIDTH)
        render-pixel x row rng

inline render-rect (x y w h iter rng)
    using import itertools
    for _x _y in (dim w h)
        render-pixel-avg (x + _x) (y + _y) iter rng

global clock : timer.Timer

fn init ()
    display.init FB_WIDTH FB_HEIGHT
    clock = (timer.Timer) # start counting from now

    # dispatch work
    va-map
        inline (i)
            threads.spawn
                fn (arg)
                    # in lieu of a proper jump function
                    local rng : PRNG.random.Xoshiro256+ (i * 1000)
                    # for y in (range FB_HEIGHT)
                    #     if ((y % THREAD_COUNT) == i)
                    #         render-row y rng

                    inline ceil (x)
                        let f = (floor x)
                        if (f < x)
                            f + 1.0
                        else
                            f 

                    let TILE_SIZE = 16
                    let rows = ((ceil (FB_HEIGHT / TILE_SIZE)) as u32)
                    let columns = ((ceil (FB_WIDTH / TILE_SIZE)) as u32)
                    using import itertools
                    for iter in (range RT_SAMPLE_COUNT)
                        for _i x y in (enumerate (dim columns rows))
                            if ((_i % THREAD_COUNT) == i)
                                let w h =
                                    (min ((x * TILE_SIZE) + TILE_SIZE) FB_WIDTH) - (x * TILE_SIZE)
                                    (min ((y * TILE_SIZE) + TILE_SIZE) FB_HEIGHT) - (y * TILE_SIZE)
                                render-rect (x * TILE_SIZE) (y * TILE_SIZE) w h iter rng

                    print "thread" i "done in" ('run-time-real clock) "seconds"
                    null as voidstar
                null
        va-range THREAD_COUNT
    ;

fn update (dt)
    ;

fn draw (cmd-encoder render-pass)
    display.update render-pass (view color-buffer)
    ;

app.run init update draw
