// PC test, OrcX01: Rexxar dies and the map's "Resurrection Effect" revives him at the active stone
// 6 s later; he must then stand there (not stay in his dissipate pose up in the sky) and walk.
//   NX_DEBUG="OrcX01@150:script:NxReviveStoneTest.j@nxrs_kill;OrcX01@158:script:NxReviveStoneTest.j@nxrs_cam;
//     OrcX01@160:shot;OrcX01@162:script:NxReviveStoneTest.j@nxrs_check;OrcX01@168:script:NxReviveStoneTest.j@nxrs_after,shot;OrcX01@170:quit"
native NxLog takes string s returns nothing

function nxrs_where takes string tag returns nothing
    local unit u = udg_Rexxar
    call NxLog("NXTEST " + tag + ": Rexxar at " + I2S(R2I(GetUnitX(u))) + "," + I2S(R2I(GetUnitY(u))) + " life " + I2S(R2I(GetWidgetLife(u))) + " hidden " + I2S(IntegerTertiaryOp(IsUnitHidden(u), 1, 0)) + " paused " + I2S(IntegerTertiaryOp(IsUnitPaused(u), 1, 0)) + " order " + I2S(GetUnitCurrentOrder(u)))
    if udg_ResurrectionStone != null then
        call NxLog("NXTEST " + tag + ": stone unit at " + I2S(R2I(GetUnitX(udg_ResurrectionStone))) + "," + I2S(R2I(GetUnitY(udg_ResurrectionStone))))
    endif
    if udg_ResurrectionSpot != null then
        call NxLog("NXTEST " + tag + ": revive spot " + I2S(R2I(GetLocationX(udg_ResurrectionSpot))) + "," + I2S(R2I(GetLocationY(udg_ResurrectionSpot))))
    endif
endfunction

function nxrs_kill takes nothing returns nothing
    if udg_ResurrectionSpot == null then
        // no stone picked yet this early: the first one, as approaching it would do
        set udg_ResurrectionSpot = GetRectCenter(gg_rct_Resurrection_Stone_01_Spot)
    endif
    call nxrs_where("before death")
    call KillUnit(udg_Rexxar)
endfunction

function nxrs_check takes nothing returns nothing
    call nxrs_where("after revive")
    call NxLog("NXTEST move order accepted " + I2S(IntegerTertiaryOp(IssuePointOrder(udg_Rexxar, "move", GetUnitX(udg_Rexxar) + 400, GetUnitY(udg_Rexxar)), 1, 0)))
endfunction

function nxrs_after takes nothing returns nothing
    call nxrs_where("5 s after the move order")
endfunction

function nxrs_cam takes nothing returns nothing
    call SetCameraPosition(GetUnitX(udg_Rexxar), GetUnitY(udg_Rexxar))
endfunction
