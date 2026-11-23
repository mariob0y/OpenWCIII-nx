// warsmash-nx PC test (NX_DEBUG step "script:NxHexTest.j@nxh_main"): Hex and Polymorph turn the target
// into a critter (model swap, NxHex) and the unit comes back when the buff ends. Pair with "shot" steps
// ~3 s after (critters) and ~22 s after (originals back). Results: "NXTEST" lines on stderr.
native NxLog takes string s returns nothing

globals
    unit nxh_rok = null
    unit nxh_sor = null
    unit array nxh_foe
    real nxh_x = 0
    real nxh_y = 0
endglobals

function nxh_b2s takes boolean b returns string
    if b then
        return "true"
    endif
    return "false"
endfunction

function nxh_state takes string when returns nothing
    local integer i = 0
    loop
        exitwhen i >= 3
        call NxLog("NXTEST " + when + " foe " + I2S(i) + " type " + I2S(GetUnitTypeId(nxh_foe[i])) + " hex " + I2S(GetUnitAbilityLevel(nxh_foe[i], 'BOhx')) + " poly " + I2S(GetUnitAbilityLevel(nxh_foe[i], 'Bply')) + " speed " + R2S(GetUnitMoveSpeed(nxh_foe[i])) + " life " + I2S(R2I(GetWidgetLife(nxh_foe[i]))) + " at " + I2S(R2I(GetUnitX(nxh_foe[i]))) + "," + I2S(R2I(GetUnitY(nxh_foe[i]))) + " visible " + nxh_b2s(IsUnitVisible(nxh_foe[i], Player(0))))
        set i = i + 1
    endloop
endfunction

function nxh_after takes nothing returns nothing
    call nxh_state("after")
    call NxLog("NXTEST done")
endfunction

function nxh_during takes nothing returns nothing
    call nxh_state("during")
    call TimerStart(CreateTimer(), 18.0, false, function nxh_after)
endfunction

function nxh_cast2 takes nothing returns nothing
    call NxLog("NXTEST order hex air: " + I2S(GetUnitAbilityLevel(nxh_rok, 'ANhx')) + " " + R2S(GetUnitState(nxh_rok, UNIT_STATE_MANA)))
    call IssueTargetOrder(nxh_rok, "hex", nxh_foe[2])
    call TimerStart(CreateTimer(), 2.0, false, function nxh_during)
endfunction

function nxh_cast takes nothing returns nothing
    call NxLog("NXTEST order hex: " + nxh_b2s(IssueTargetOrder(nxh_rok, "hex", nxh_foe[0])) + " Rhst " + I2S(GetPlayerTechCount(Player(0), 'Rhst', true)) + " by id " + nxh_b2s(IssueTargetOrderById(nxh_sor, 852074, nxh_foe[1])))
    call NxLog("NXTEST order polymorph: " + nxh_b2s(IssueTargetOrder(nxh_sor, "polymorph", nxh_foe[1])) + " mana " + R2S(GetUnitState(nxh_sor, UNIT_STATE_MANA)) + " level " + I2S(GetUnitAbilityLevel(nxh_sor, 'Aply')))
    call TimerStart(CreateTimer(), 2.5, false, function nxh_cast2)
endfunction

function nxh_main takes nothing returns nothing
    local location l = GetCameraTargetPositionLoc()
    local integer i = 0
    set nxh_x = GetLocationX(l)
    set nxh_y = GetLocationY(l)
    call NxLog("NXTEST hex test at " + R2S(nxh_x) + "," + R2S(nxh_y))
    call SetPlayerTechResearched(Player(0), 'Rhst', 2)
    set nxh_rok = CreateUnit(Player(0), 'Orkn', nxh_x - 150, nxh_y, 0)
    call SetHeroLevel(nxh_rok, 10, false)
    call SelectHeroSkill(nxh_rok, 'ANhx')
    call SetUnitState(nxh_rok, UNIT_STATE_MANA, 1000)
    set nxh_sor = CreateUnit(Player(0), 'hsor', nxh_x - 150, nxh_y + 100, 0)
    call SetUnitState(nxh_sor, UNIT_STATE_MANA, 1000)
    call SetUnitAcquireRange(nxh_rok, 0)
    call SetUnitAcquireRange(nxh_sor, 0)
    set nxh_foe[0] = CreateUnit(Player(PLAYER_NEUTRAL_AGGRESSIVE), 'hfoo', nxh_x + 150, nxh_y - 80, 180)
    set nxh_foe[1] = CreateUnit(Player(PLAYER_NEUTRAL_AGGRESSIVE), 'hkni', nxh_x + 150, nxh_y + 80, 180)
    set nxh_foe[2] = CreateUnit(Player(PLAYER_NEUTRAL_AGGRESSIVE), 'hgry', nxh_x + 250, nxh_y, 180)
    loop
        exitwhen i >= 3
        call SetUnitAcquireRange(nxh_foe[i], 0)
        set i = i + 1
    endloop
    call SetCameraPosition(nxh_x + 50, nxh_y)
    call SetCameraField(CAMERA_FIELD_TARGET_DISTANCE, 1000, 0)
    call SetDayNightModels("", "")
    call SetTimeOfDay(12)
    call nxh_state("before")
    call TimerStart(CreateTimer(), 1.5, false, function nxh_cast)
endfunction
