// warsmash-nx PC test (NX_DEBUG step "script:NxSpellTest3.j@nx3_main"): the second batch of Durotar spells
// (NxSpells2): Far Sight, Earthquake, Cyclone, Pulverize, Feedback, Burning Oil, Wander.
// Every result is a line starting "NXTEST" on stderr.
native NxLog takes string s returns nothing

globals
    unit nx3_thrall = null
    unit nx3_tauren = null
    unit nx3_breaker = null
    unit nx3_demo = null
    unit nx3_harpy = null
    unit nx3_pig = null
    unit nx3_tower = null
    unit nx3_farm = null
    unit array nx3_foe
    real nx3_x = 0
    real nx3_y = 0
    real nx3_pigx = 0
    real nx3_pigy = 0
endglobals

function nx3_b2s takes boolean b returns string
    if b then
        return "true"
    endif
    return "false"
endfunction

function nx3_lives takes nothing returns string
    local integer i = 0
    local string s = ""
    loop
        exitwhen i >= 4
        set s = s + " " + I2S(R2I(GetWidgetLife(nx3_foe[i])))
        set i = i + 1
    endloop
    return s
endfunction

function nx3_end takes nothing returns nothing
    call NxLog("NXTEST foes around the tauren at the end (start 420; Pulverize hits all of them):" + nx3_lives())
    call NxLog("NXTEST priest mana at the end " + I2S(R2I(GetUnitState(nx3_foe[3], UNIT_STATE_MANA))) + " (spell breaker Feedback)")
    call NxLog("NXTEST tower life at the end " + I2S(R2I(GetWidgetLife(nx3_tower))) + " (Earthquake 50/s)")
    call NxLog("NXTEST farm life at the end " + I2S(R2I(GetWidgetLife(nx3_farm))) + " (demolisher + Burning Oil)")
    call NxLog("NXTEST pig moved: " + nx3_b2s(GetUnitX(nx3_pig) != nx3_pigx or GetUnitY(nx3_pig) != nx3_pigy))
    call NxLog("NXTEST done")
endfunction

function nx3_mid takes nothing returns nothing
    call NxLog("NXTEST breaker order " + OrderId2String(GetUnitCurrentOrder(nx3_breaker)) + ", demolisher order " + OrderId2String(GetUnitCurrentOrder(nx3_demo)) + " farm " + I2S(R2I(GetWidgetLife(nx3_farm))))
    call NxLog("NXTEST cyclone on tauren: level " + I2S(GetUnitAbilityLevel(nx3_tauren, 'Bcyc')) + " life " + I2S(R2I(GetWidgetLife(nx3_tauren))))
    call NxLog("NXTEST earthquake slow on footman: " + I2S(GetUnitAbilityLevel(nx3_foe[0], 'BOeq')) + " speed " + R2S(GetUnitMoveSpeed(nx3_foe[0])))
    call NxLog("NXTEST thrall far sight cooldown " + R2S(BlzGetUnitAbilityCooldownRemaining(nx3_thrall, 'AOfs')) + " far sight: point visible " + nx3_b2s(IsVisibleToPlayer(nx3_x + 1500, nx3_y, Player(0))))
    call NxLog("NXTEST mid foes:" + nx3_lives() + " tower " + I2S(R2I(GetWidgetLife(nx3_tower))))
    call TimerStart(CreateTimer(), 10.0, false, function nx3_end)
endfunction

function nx3_cast takes nothing returns nothing
    call NxLog("NXTEST demolisher attack order: " + nx3_b2s(IssueTargetOrder(nx3_demo, "attack", nx3_farm)) + " Abof " + I2S(GetUnitAbilityLevel(nx3_demo, 'Abof')) + " farm life " + I2S(R2I(GetWidgetLife(nx3_farm))))
    call NxLog("NXTEST breaker attack order: " + nx3_b2s(IssueTargetOrder(nx3_breaker, "attack", nx3_foe[3])) + " Afbk " + I2S(GetUnitAbilityLevel(nx3_breaker, 'Afbk')) + " tauren Awar " + I2S(GetUnitAbilityLevel(nx3_tauren, 'Awar')))
    call NxLog("NXTEST far sight before: point visible " + nx3_b2s(IsVisibleToPlayer(nx3_x + 1500, nx3_y, Player(0))))
    call NxLog("NXTEST order farsight: " + nx3_b2s(IssuePointOrder(nx3_thrall, "farsight", nx3_x + 1500, nx3_y)))
    call NxLog("NXTEST order cyclone: " + nx3_b2s(IssueTargetOrder(nx3_harpy, "cyclone", nx3_tauren)))
endfunction

function nx3_quake takes nothing returns nothing
    call NxLog("NXTEST order earthquake: " + nx3_b2s(IssuePointOrder(nx3_thrall, "earthquake", GetUnitX(nx3_tower), GetUnitY(nx3_tower))))
    call TimerStart(CreateTimer(), 3.0, false, function nx3_mid)
endfunction

function nx3_main takes nothing returns nothing
    local location l = GetCameraTargetPositionLoc()
    local integer i = 0
    set nx3_x = GetLocationX(l)
    set nx3_y = GetLocationY(l)
    call NxLog("NXTEST spell test 3 at " + R2S(nx3_x) + "," + R2S(nx3_y))
    call SetPlayerTechResearched(Player(0), 'Rwar', 1)
    call SetPlayerTechResearched(Player(0), 'Robf', 1)
    // Thrall: Far Sight + Earthquake, far from the fight
    set nx3_thrall = CreateUnit(Player(0), 'Othr', nx3_x - 600, nx3_y + 600, 0)
    call SetHeroLevel(nx3_thrall, 10, false)
    call SelectHeroSkill(nx3_thrall, 'AOfs')
    call SelectHeroSkill(nx3_thrall, 'AOeq')
    call SetUnitState(nx3_thrall, UNIT_STATE_MANA, 1000)
    call SetUnitAcquireRange(nx3_thrall, 0)
    call NxLog("NXTEST thrall skills: far sight " + I2S(GetUnitAbilityLevel(nx3_thrall, 'AOfs')) + " earthquake " + I2S(GetUnitAbilityLevel(nx3_thrall, 'AOeq')))
    // enemy tower for Earthquake, enemy farm for the demolisher
    set nx3_tower = CreateUnit(Player(PLAYER_NEUTRAL_AGGRESSIVE), 'hgtw', nx3_x - 300, nx3_y + 900, 0)
    set nx3_farm = CreateUnit(Player(PLAYER_NEUTRAL_AGGRESSIVE), 'hhou', nx3_x + 700, nx3_y - 600, 0)
    set nx3_demo = CreateUnit(Player(0), 'ocat', nx3_x + 300, nx3_y - 600, 0)
    // tauren among four foes (Pulverize), spell breaker on a priest (Feedback)
    set nx3_tauren = CreateUnit(Player(0), 'otau', nx3_x, nx3_y, 0)
    set nx3_foe[0] = CreateUnit(Player(PLAYER_NEUTRAL_AGGRESSIVE), 'hfoo', nx3_x + 90, nx3_y, 180)
    set nx3_foe[1] = CreateUnit(Player(PLAYER_NEUTRAL_AGGRESSIVE), 'hfoo', nx3_x - 90, nx3_y, 0)
    set nx3_foe[2] = CreateUnit(Player(PLAYER_NEUTRAL_AGGRESSIVE), 'hfoo', nx3_x, nx3_y + 90, 270)
    set nx3_foe[3] = CreateUnit(Player(PLAYER_NEUTRAL_AGGRESSIVE), 'hmpr', nx3_x, nx3_y - 90, 90)
    loop
        exitwhen i >= 4
        call SetUnitState(nx3_foe[i], UNIT_STATE_LIFE, 420)
        call SetUnitAcquireRange(nx3_foe[i], 0)
        call SetUnitInvulnerable(nx3_foe[i], false)
        set i = i + 1
    endloop
    call SetUnitState(nx3_foe[3], UNIT_STATE_MANA, 200)
    call IssueTargetOrder(nx3_tauren, "attack", nx3_foe[0])
    set nx3_breaker = CreateUnit(Player(0), 'hspt', nx3_x + 150, nx3_y - 150, 0)
    // a harpy queen for player 1 casts Cyclone on the tauren
    set nx3_harpy = CreateUnit(Player(1), 'nhrq', nx3_x - 300, nx3_y - 300, 0)
    call SetUnitState(nx3_harpy, UNIT_STATE_MANA, 1000)
    call SetUnitAcquireRange(nx3_harpy, 0)
    call SetPlayerAlliance(Player(1), Player(0), ALLIANCE_PASSIVE, false)
    call SetPlayerAlliance(Player(0), Player(1), ALLIANCE_PASSIVE, false)
    // a pig that should wander
    set nx3_pig = CreateUnit(Player(PLAYER_NEUTRAL_PASSIVE), 'npig', nx3_x - 500, nx3_y - 500, 0)
    set nx3_pigx = GetUnitX(nx3_pig)
    set nx3_pigy = GetUnitY(nx3_pig)
    call SetCameraPosition(nx3_x, nx3_y)
    call TimerStart(CreateTimer(), 1.5, false, function nx3_cast)
    call TimerStart(CreateTimer(), 4.0, false, function nx3_quake)
endfunction
