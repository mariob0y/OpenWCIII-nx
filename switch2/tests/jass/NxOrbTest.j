// warsmash-nx PC test (NX_DEBUG step "script:NxOrbTest.j@nxo_main"): an orb (Orb of Fire) switches on a
// melee hero's second weapon, so Cairne can attack a flying harpy; its life is logged every second.
// Results: "NXTEST" lines on stderr.
native NxLog takes string s returns nothing

globals
    unit nxo_hero = null
    unit nxo_harpy = null
    integer nxo_n = 0
endglobals

function nxo_b2s takes boolean b returns string
    if b then
        return "true"
    endif
    return "false"
endfunction

function nxo_tick takes nothing returns nothing
    set nxo_n = nxo_n + 1
    call NxLog("NXTEST t+" + I2S(nxo_n) + " harpy life " + I2S(R2I(GetWidgetLife(nxo_harpy))) + " cairne order " + OrderId2String(GetUnitCurrentOrder(nxo_hero)))
    if nxo_n >= 8 then
        call PauseTimer(GetExpiredTimer())
        call NxLog("NXTEST done")
    endif
endfunction

function nxo_go takes nothing returns nothing
    call NxLog("NXTEST attack without orb: " + nxo_b2s(IssueTargetOrder(nxo_hero, "attack", nxo_harpy)))
    call UnitAddItemById(nxo_hero, 'ofir')
    call NxLog("NXTEST attack with Orb of Fire: " + nxo_b2s(IssueTargetOrder(nxo_hero, "attack", nxo_harpy)) + " harpy life " + I2S(R2I(GetWidgetLife(nxo_harpy))))
    call TimerStart(CreateTimer(), 1.0, true, function nxo_tick)
endfunction

function nxo_main takes nothing returns nothing
    local location l = GetCameraTargetPositionLoc()
    local real x = GetLocationX(l) + 1500
    local real y = GetLocationY(l) - 1500
    set nxo_hero = CreateUnit(Player(0), 'Ocbh', x, y, 0)
    call SetUnitAcquireRange(nxo_hero, 0)
    set nxo_harpy = CreateUnit(Player(PLAYER_NEUTRAL_AGGRESSIVE), 'nhar', x + 300, y, 180)
    call SetUnitAcquireRange(nxo_harpy, 0)
    call SetCameraPosition(x, y)
    call TimerStart(CreateTimer(), 2.0, false, function nxo_go)
endfunction
