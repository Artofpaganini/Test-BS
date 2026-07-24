package com.onexui.bottomsheet.state

import com.onexui.bottomsheet.behavior.defaultXBottomSheetBehavior
import com.onexui.bottomsheet.config.XBottomSheetDsl
import com.onexui.bottomsheet.state.builder.BehaviorBuilder
import com.onexui.bottomsheet.state.builder.StructureBuilder
import com.onexui.bottomsheet.state.builder.StyleBuilder
import com.onexui.bottomsheet.style.defaultXBottomSheetStyle

@XBottomSheetDsl
internal class XBottomSheetStateBuilder {
    internal val structureBuilder = StructureBuilder()
    internal val behaviorBuilder = BehaviorBuilder(defaultXBottomSheetBehavior())
    internal val styleBuilder = StyleBuilder(defaultXBottomSheetStyle())

    inline fun structure(configure: StructureBuilder.() -> Unit) {
        structureBuilder.configure()
    }

    inline fun behavior(configure: BehaviorBuilder.() -> Unit) {
        behaviorBuilder.configure()
    }

    inline fun style(configure: StyleBuilder.() -> Unit) {
        styleBuilder.configure()
    }

    internal fun buildState(): XBottomSheetState = XBottomSheetState(
        isSkipCollapsed = structureBuilder.isSkipCollapsed,
        isInitialLoading = structureBuilder.isInitialLoading,
        anchors = structureBuilder.buildAnchors(),
        behavior = behaviorBuilder.build(),
        style = styleBuilder.build(),
    )
}
