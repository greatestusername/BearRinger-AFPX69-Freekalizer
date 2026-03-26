package com.freekalizer.sampler

/**
 * Where looped sampler audio is summed in the graph.
 *
 * [THROUGH_EFFECTS_PATH]: sample is written to the effects/master-pitch bus first (identity pass today;
 * E4 effects and E3 main pitch apply here before the bus is mixed to output).
 *
 * [DIRECT_TO_MONITOR_MIX]: sample is mixed straight with live monitoring, bypassing that chain.
 */
enum class SamplerFxRouteIntent {
    THROUGH_EFFECTS_PATH,
    DIRECT_TO_MONITOR_MIX
}
