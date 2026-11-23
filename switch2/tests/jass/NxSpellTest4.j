// warsmash-nx PC test (NX_DEBUG step "script:NxSpellTest4.j@nx4_main"): Spiked Barricades (Aspi with the
// Rosp upgrade) and Abolish Magic (Aadm). Results: "NXTEST" lines on stderr.
native NxLog takes string s returns nothing

globals
    unit nx4_barracks = null
    unit nx4_foot = null
    unit nx4_shaman = null
    unit nx4_grunt = null
    unit nx4_geo = null
    real nx4_x = 0
    real nx4_y = 0
endglobals

function nx4_b2s takes boolean b returns string
    if b then
        return "true"
    endif
    return "false"
endfunction

function nx4_end takes nothing returns nothing
    call NxLog("NXTEST footman life after hitting the barracks: " + I2S(R2I(GetWidgetLife(nx4_foot))) + " (420 at start; barracks " + I2S(R2I(GetWidgetLife(nx4_barracks))) + ")")
    call NxLog("NXTEST grunt bloodlust after abolish magic: " + I2S(GetUnitAbilityLevel(nx4_grunt, 'Bblo')))
    call NxLog("NXTEST done")
endfunction

function nx4_abolish takes nothing returns nothing
    call NxLog("NXTEST grunt bloodlust before: " + I2S(GetUnitAbilityLevel(nx4_grunt, 'Bblo')) + ", geomancer Aadm " + I2S(GetUnitAbilityLevel(nx4_geo, 'Aadm')) + " ACdm " + I2S(GetUnitAbilityLevel(nx4_geo, 'ACdm')))
    call NxLog("NXTEST order autodispel: " + nx4_b2s(IssueTargetOrderById(nx4_geo, 852132, nx4_grunt)))
    call TimerStart(CreateTimer(), 8.0, false, function nx4_end)
endfunction

function nx4_orders takes nothing returns nothing
    call NxLog("NXTEST footman attack order: " + nx4_b2s(IssueTargetOrder(nx4_foot, "attack", nx4_barracks)) + " Aspi " + I2S(GetUnitAbilityLevel(nx4_barracks, 'Aspi')) + " Rosp " + I2S(GetPlayerTechCount(Player(0), 'Rosp', true)))
    call NxLog("NXTEST bloodlust order: " + nx4_b2s(IssueTargetOrder(nx4_shaman, "bloodlust", nx4_grunt)))
    call TimerStart(CreateTimer(), 2.0, false, function nx4_abolish)
endfunction

function nx4_main takes nothing returns nothing
    local location l = GetCameraTargetPositionLoc()
    set nx4_x = GetLocationX(l)
    set nx4_y = GetLocationY(l)
    call NxLog("NXTEST spell test 4 at " + R2S(nx4_x) + "," + R2S(nx4_y))
    call SetPlayerTechResearched(Player(0), 'Rosp', 1)
    set nx4_barracks = CreateUnit(Player(0), 'obar', nx4_x + 300, nx4_y, 0)
    set nx4_foot = CreateUnit(Player(PLAYER_NEUTRAL_AGGRESSIVE), 'hfoo', nx4_x + 450, nx4_y + 200, 0)
    call SetUnitAcquireRange(nx4_foot, 0)
    set nx4_shaman = CreateUnit(Player(0), 'oshm', nx4_x - 200, nx4_y, 0)
    call SetUnitState(nx4_shaman, UNIT_STATE_MANA, 500)
    call SetUnitAcquireRange(nx4_shaman, 0)
    set nx4_grunt = CreateUnit(Player(0), 'ogru', nx4_x - 100, nx4_y - 100, 0)
    call SetUnitAcquireRange(nx4_grunt, 0)
    set nx4_geo = CreateUnit(Player(PLAYER_NEUTRAL_AGGRESSIVE), 'nkog', nx4_x - 300, nx4_y - 250, 0)
    call SetUnitState(nx4_geo, UNIT_STATE_MANA, 500)
    call SetUnitAcquireRange(nx4_geo, 0)
    call SetUnitInvulnerable(nx4_geo, true)
    call TimerStart(CreateTimer(), 1.5, false, function nx4_orders)
endfunction
