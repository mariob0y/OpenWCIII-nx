// warsmash-nx PC test (NX_DEBUG step "script:NxItemTest3.j@nxk_main"): the passive Durotar items (NxItems):
// Ring of Regeneration, the orbs (Frost, Lightning, Slow, Venom, Fire v2) on melee heroes against harpies,
// Cloak of Flames, Orgrimmar Battle Standard. Every result is a line starting "NXTEST" on stderr.
native NxLog takes string s returns nothing

globals
    unit nxk_ring = null
    unit nxk_plain = null
    unit nxk_cloak = null
    unit nxk_victim = null
    unit array nxk_hero
    unit array nxk_harpy
    integer array nxk_orb
    integer array nxk_seen
    real nxk_x = 0
    real nxk_y = 0
    integer nxk_n = 0
endglobals

function nxk_life takes unit u returns string
    return I2S(R2I(GetWidgetLife(u)))
endfunction

function nxk_tick takes nothing returns nothing
    local integer i = 0
    local string s
    set nxk_n = nxk_n + 1
    loop
        exitwhen i >= 5
        if GetUnitCurrentOrder(nxk_hero[i]) != OrderId("attack") then
            call IssueTargetOrder(nxk_hero[i], "attack", nxk_harpy[i])
        endif
        if GetUnitAbilityLevel(nxk_harpy[i], 'Bfro') + GetUnitAbilityLevel(nxk_harpy[i], 'Bprg') + GetUnitAbilityLevel(nxk_harpy[i], 'Bslo') + GetUnitAbilityLevel(nxk_harpy[i], 'BIpb') > 0 then
            set nxk_seen[i] = nxk_seen[i] + 1
        endif
        set s = "NXTEST t+" + I2S(nxk_n) + " orb " + I2S(i) + " harpy life " + nxk_life(nxk_harpy[i]) + " speed " + I2S(R2I(GetUnitMoveSpeed(nxk_harpy[i])))
        set s = s + " Bfro " + I2S(GetUnitAbilityLevel(nxk_harpy[i], 'Bfro')) + " Bprg " + I2S(GetUnitAbilityLevel(nxk_harpy[i], 'Bprg')) + " Bslo " + I2S(GetUnitAbilityLevel(nxk_harpy[i], 'Bslo')) + " BIpb " + I2S(GetUnitAbilityLevel(nxk_harpy[i], 'BIpb'))
        call NxLog(s)
        set i = i + 1
    endloop
    call NxLog("NXTEST cloak t+" + I2S(nxk_n) + " footman life " + nxk_life(nxk_victim) + " blademaster life " + nxk_life(nxk_cloak) + " footman order " + OrderId2String(GetUnitCurrentOrder(nxk_victim)))
    if nxk_n == 1 then
        call NxLog("NXTEST at t+1: harpy0 " + R2S(GetUnitX(nxk_harpy[0])) + "," + R2S(GetUnitY(nxk_harpy[0])) + " footman " + R2S(GetUnitX(nxk_victim)) + "," + R2S(GetUnitY(nxk_victim)) + " order " + OrderId2String(GetUnitCurrentOrder(nxk_victim)))
    endif
    if nxk_n == 5 then
        call NxLog("NXTEST regeneration over 5 s: with ring " + nxk_life(nxk_ring) + " without " + nxk_life(nxk_plain) + " (both from 300; ring +2/s)")
        call NxLog("NXTEST cloak of flames: footman life " + nxk_life(nxk_victim) + " (from 420, 10/s) at " + I2S(R2I(SquareRoot((GetUnitX(nxk_victim) - GetUnitX(nxk_cloak)) * (GetUnitX(nxk_victim) - GetUnitX(nxk_cloak)) + (GetUnitY(nxk_victim) - GetUnitY(nxk_cloak)) * (GetUnitY(nxk_victim) - GetUnitY(nxk_cloak))))) + " (area 160)")
    endif
    if nxk_n >= 15 then
        set i = 0
        loop
            exitwhen i >= 5
            call NxLog("NXTEST orb " + I2S(i) + " summary: harpy lost " + I2S(2000 - R2I(GetWidgetLife(nxk_harpy[i]))) + " life, debuff seen on " + I2S(nxk_seen[i]) + " of 15 ticks")
            set i = i + 1
        endloop
        call PauseTimer(GetExpiredTimer())
        call NxLog("NXTEST done")
    endif
endfunction

function nxk_hit takes nothing returns nothing
    if nxk_n < 4 then
        call NxLog("NXTEST footman damaged " + R2S(GetEventDamage()) + " by " + GetUnitName(GetEventDamageSource()) + " at t+" + I2S(nxk_n))
    endif
endfunction

function nxk_go takes nothing returns nothing
    local integer i = 0
    local trigger t = CreateTrigger()
    call TriggerRegisterUnitEvent(t, nxk_victim, EVENT_UNIT_DAMAGED)
    call TriggerAddAction(t, function nxk_hit)
    loop
        exitwhen i >= 5
        call UnitAddItemById(nxk_hero[i], nxk_orb[i])
        if not IsUnitVisible(nxk_harpy[i], Player(0)) then
            call NxLog("NXTEST orb " + I2S(i) + " harpy not visible")
        endif
        if IssueTargetOrder(nxk_hero[i], "attack", nxk_harpy[i]) then
            call NxLog("NXTEST orb " + I2S(i) + " attack order: true")
        else
            call NxLog("NXTEST orb " + I2S(i) + " attack order: false")
        endif
        set i = i + 1
    endloop
    // invulnerable so nearby map creeps cannot spoil the comparison (they still regenerate)
    call SetUnitInvulnerable(nxk_ring, true)
    call SetUnitInvulnerable(nxk_plain, true)
    call SetWidgetLife(nxk_ring, 300)
    call SetWidgetLife(nxk_plain, 300)
    call UnitAddItemById(nxk_ring, 'rlif')
    call UnitAddItemById(nxk_ring, 'btst')
    call UnitAddItemById(nxk_cloak, 'clfm')
    call TimerStart(CreateTimer(), 1.0, true, function nxk_tick)
endfunction

function nxk_main takes nothing returns nothing
    local location l = GetCameraTargetPositionLoc()
    local integer i = 0
    set nxk_x = GetLocationX(l)
    set nxk_y = GetLocationY(l)
    call NxLog("NXTEST item test 3 at " + R2S(nxk_x) + "," + R2S(nxk_y))
    set nxk_orb[0] = 'ofro'
    set nxk_orb[1] = 'oli2'
    set nxk_orb[2] = 'oslo'
    set nxk_orb[3] = 'oven'
    set nxk_orb[4] = 'ofr2'
    loop
        exitwhen i >= 5
        set nxk_hero[i] = CreateUnit(Player(0), 'Ocbh', nxk_x - 250 + i * 250, nxk_y - 450, 90)
        call SetUnitAcquireRange(nxk_hero[i], 0)
        set nxk_harpy[i] = CreateUnit(Player(PLAYER_NEUTRAL_AGGRESSIVE), 'nhar', nxk_x - 250 + i * 250, nxk_y - 150, 270)
        call SetUnitAcquireRange(nxk_harpy[i], 0)
        call SetUnitState(nxk_harpy[i], UNIT_STATE_MAX_LIFE, 2000)
        call SetWidgetLife(nxk_harpy[i], 2000)
        set i = i + 1
    endloop
    // regeneration: two Tauren Chieftains away from the fight
    set nxk_ring = CreateUnit(Player(0), 'Otch', nxk_x - 600, nxk_y + 600, 0)
    set nxk_plain = CreateUnit(Player(0), 'Otch', nxk_x - 900, nxk_y + 600, 0)
    // Cloak of Flames: a blademaster next to a footman that does not fight back
    set nxk_cloak = CreateUnit(Player(0), 'Obla', nxk_x + 200, nxk_y + 250, 0)
    call SetUnitAcquireRange(nxk_cloak, 0)
    set nxk_victim = CreateUnit(Player(PLAYER_NEUTRAL_AGGRESSIVE), 'hfoo', nxk_x + 280, nxk_y + 250, 180)
    call SetUnitAcquireRange(nxk_victim, 0)
    call SetWidgetLife(nxk_victim, 420)
    call NxLog("NXTEST placed: hero0 " + R2S(GetUnitX(nxk_hero[0])) + "," + R2S(GetUnitY(nxk_hero[0])) + " harpy0 " + R2S(GetUnitX(nxk_harpy[0])) + "," + R2S(GetUnitY(nxk_harpy[0])) + " cloak " + R2S(GetUnitX(nxk_cloak)) + "," + R2S(GetUnitY(nxk_cloak)) + " footman " + R2S(GetUnitX(nxk_victim)) + "," + R2S(GetUnitY(nxk_victim)))
    call SetCameraPosition(nxk_x, nxk_y)
    call TimerStart(CreateTimer(), 2.0, false, function nxk_go)
endfunction
