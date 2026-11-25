// warsmash-nx PC test (NX_DEBUG "OrcX01@5:key:ESCAPE;OrcX01@10:script:NxMishaSkipTest.j@nxm_main"): skipping
// OrcX01's intro must still give Rexxar his bear. The intro cleanup removes the cinematic Misha and orders
// Summon Misha (150 mana) at once, so a new hero needs its Intelligence mana from the start, and the map's
// "Misha Summoned" trigger (EVENT_PLAYER_UNIT_SUMMON) must set udg_Misha and refill Rexxar's mana.
// Expect: one bear, "is udg_Misha true", Rexxar mana 270.
native NxLog takes string s returns nothing

function nxm_b takes boolean b returns string
    if b then
        return "true"
    endif
    return "false"
endfunction

function nxm_count takes nothing returns boolean
    local integer t = GetUnitTypeId(GetFilterUnit())
    return t == 'ngzc' or t == 'ngzd' or t == 'ngza' or t == 'ngz4'
endfunction

function nxm_main takes nothing returns nothing
    local group g = CreateGroup()
    local unit u
    call GroupEnumUnitsOfPlayer(g, Player(0), Condition(function nxm_count))
    call NxLog("NXTEST rexxar order " + OrderId2String(GetUnitCurrentOrder(udg_Rexxar)) + " mana " + R2S(GetUnitState(udg_Rexxar, UNIT_STATE_MANA)) + " paused " + nxm_b(IsUnitPaused(udg_Rexxar)) + " hidden " + nxm_b(IsUnitHidden(udg_Rexxar)) + " Arsg lvl " + I2S(GetUnitAbilityLevel(udg_Rexxar, 'Arsg')) + " skipped " + nxm_b(udg_IntroSkipped) + " incine " + nxm_b(udg_InCinematic))
    loop
        set u = FirstOfGroup(g)
        exitwhen u == null
        call GroupRemoveUnit(g, u)
        call NxLog("NXTEST bear " + UnitId2String(GetUnitTypeId(u)) + " alive " + nxm_b(GetUnitState(u, UNIT_STATE_LIFE) > 0) + " hidden " + nxm_b(IsUnitHidden(u)) + " is udg_Misha " + nxm_b(u == udg_Misha))
    endloop
    call NxLog("NXTEST misha var alive " + nxm_b(GetUnitState(udg_Misha, UNIT_STATE_LIFE) > 0))
endfunction
