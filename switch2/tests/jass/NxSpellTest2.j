// warsmash-nx PC test (NX_DEBUG step "script:NxSpellTest2.j@nx2_main"): the campaign spells added in
// round W3r (NxSpells, the re-enabled human hero spells) and the computer units' spell casting (NxUnitAI).
// Every result is a line starting "NXTEST" on stderr.
native NxLog takes string s returns nothing

globals
    unit nx2_rok = null
    unit nx2_chen = null
    unit nx2_shm = null
    unit nx2_rai = null
    unit nx2_grunt = null
    unit nx2_bsk = null
    unit nx2_doc = null
    unit array nx2_foe
    unit nx2_mk = null
    unit nx2_pal = null
    unit nx2_hurt = null
    unit nx2_caster = null
    unit array nx2_bait
    real nx2_x = 0
    real nx2_y = 0
endglobals

function nx2_b2s takes boolean b returns string
    if b then
        return "true"
    endif
    return "false"
endfunction

function nx2_lives takes nothing returns string
    local integer i = 0
    local string s = ""
    loop
        exitwhen i >= 6
        set s = s + " " + I2S(R2I(GetWidgetLife(nx2_foe[i])))
        set i = i + 1
    endloop
    return s
endfunction

function nx2_baitLives takes nothing returns string
    local integer i = 0
    local string s = ""
    loop
        exitwhen i >= 4
        set s = s + " " + I2S(R2I(GetWidgetLife(nx2_bait[i])))
        set i = i + 1
    endloop
    return s
endfunction

function nx2_final takes nothing returns nothing
    call NxLog("NXTEST foe lives at the end:" + nx2_lives())
    call NxLog("NXTEST burn buff BNbf on foe 4: " + I2S(GetUnitAbilityLevel(nx2_foe[4], 'BNbf')))
    call NxLog("NXTEST rokhan life " + I2S(R2I(GetWidgetLife(nx2_rok))) + " (set to 300 before Locust Swarm)")
    call NxLog("NXTEST AI: player 0 bait lives (start 420 each):" + nx2_baitLives())
    call NxLog("NXTEST AI: hurt creep life " + I2S(R2I(GetWidgetLife(nx2_hurt))) + " (set to 100, paladin should heal it)")
    call NxLog("NXTEST AI: mountain king mana " + I2S(R2I(GetUnitState(nx2_mk, UNIT_STATE_MANA))) + " (started at 1000)")
    call NxLog("NXTEST done")
endfunction

function nx2_second takes nothing returns nothing
    call NxLog("NXTEST hex buff BOhx on foe 0: " + I2S(GetUnitAbilityLevel(nx2_foe[0], 'BOhx')) + ", move speed " + R2S(GetUnitMoveSpeed(nx2_foe[0])))
    call NxLog("NXTEST bloodlust buff Bblo on grunt: " + I2S(GetUnitAbilityLevel(nx2_grunt, 'Bblo')) + ", move speed " + R2S(GetUnitMoveSpeed(nx2_grunt)))
    call NxLog("NXTEST ensnare buff Beng on foe 1: " + I2S(GetUnitAbilityLevel(nx2_foe[1], 'Beng')))
    call NxLog("NXTEST drunken haze buff BNdh on foe 2: " + I2S(GetUnitAbilityLevel(nx2_foe[2], 'BNdh')))
    call NxLog("NXTEST berserk buff Bbsk on berserker: " + I2S(GetUnitAbilityLevel(nx2_bsk, 'Bbsk')))
    call SetUnitState(nx2_shm, UNIT_STATE_MANA, 500)
    call SetWidgetLife(nx2_rok, 300)
    call NxLog("NXTEST order lightningshield: " + nx2_b2s(IssueTargetOrder(nx2_shm, "lightningshield", nx2_foe[3])))
    call NxLog("NXTEST order breathoffire: " + nx2_b2s(IssuePointOrder(nx2_chen, "breathoffire", GetUnitX(nx2_foe[4]), GetUnitY(nx2_foe[4]))))
    call NxLog("NXTEST order locustswarm: " + nx2_b2s(IssueImmediateOrder(nx2_rok, "locustswarm")))
    call NxLog("NXTEST order stasistrap: " + nx2_b2s(IssuePointOrder(nx2_doc, "stasistrap", GetUnitX(nx2_foe[5]) - 100, GetUnitY(nx2_foe[5]))))
    call NxLog("NXTEST foe lives before:" + nx2_lives())
    call TimerStart(CreateTimer(), 12.0, false, function nx2_final)
endfunction

function nx2_first takes nothing returns nothing
    call NxLog("NXTEST order hex: " + nx2_b2s(IssueTargetOrder(nx2_rok, "hex", nx2_foe[0])))
    call NxLog("NXTEST order bloodlust: " + nx2_b2s(IssueTargetOrder(nx2_shm, "bloodlust", nx2_grunt)))
    call NxLog("NXTEST order ensnare: " + nx2_b2s(IssueTargetOrder(nx2_rai, "ensnare", nx2_foe[1])))
    call NxLog("NXTEST chen mana " + R2S(GetUnitState(nx2_chen, UNIT_STATE_MANA)) + " order " + I2S(GetUnitCurrentOrder(nx2_chen)))
    call NxLog("NXTEST order drunkenhaze: " + nx2_b2s(IssueTargetOrder(nx2_chen, "drunkenhaze", nx2_foe[2])) + " by id " + nx2_b2s(IssueTargetOrderById(nx2_chen, 852585, nx2_foe[2])))
    call NxLog("NXTEST order berserk: " + nx2_b2s(IssueImmediateOrder(nx2_bsk, "berserk")))
    call TimerStart(CreateTimer(), 3.0, false, function nx2_second)
endfunction

function nx2_hero takes integer id, real x, real y, integer level returns unit
    local unit u = CreateUnit(Player(0), id, x, y, 0)
    call SetHeroLevel(u, level, false)
    call SetUnitState(u, UNIT_STATE_MANA, 1000)
    return u
endfunction

function nx2_main takes nothing returns nothing
    local location l = GetCameraTargetPositionLoc()
    local integer i = 0
    set nx2_x = GetLocationX(l)
    set nx2_y = GetLocationY(l)
    call NxLog("NXTEST spell test 2 at " + R2S(nx2_x) + "," + R2S(nx2_y))
    call SetPlayerTechResearched(Player(0), 'Robs', 1)
    call SetPlayerTechResearched(Player(0), 'Rost', 2)
    set nx2_rok = nx2_hero('Orkn', nx2_x, nx2_y, 10)
    call SelectHeroSkill(nx2_rok, 'ANhx')
    call SelectHeroSkill(nx2_rok, 'AOls')
    set nx2_chen = nx2_hero('Nsjs', nx2_x, nx2_y + 100, 6)
    call SelectHeroSkill(nx2_chen, 'Acdh')
    call SelectHeroSkill(nx2_chen, 'ANcf')
    call NxLog("NXTEST levels: hex " + I2S(GetUnitAbilityLevel(nx2_rok, 'ANhx')) + " locust " + I2S(GetUnitAbilityLevel(nx2_rok, 'AOls')) + " haze " + I2S(GetUnitAbilityLevel(nx2_chen, 'Acdh')) + " breath " + I2S(GetUnitAbilityLevel(nx2_chen, 'ANcf')))
    set nx2_shm = CreateUnit(Player(0), 'oshm', nx2_x - 100, nx2_y, 0)
    call SetUnitState(nx2_shm, UNIT_STATE_MANA, 500)
    set nx2_rai = CreateUnit(Player(0), 'orai', nx2_x - 100, nx2_y + 100, 0)
    set nx2_grunt = CreateUnit(Player(0), 'ogru', nx2_x - 200, nx2_y, 0)
    set nx2_bsk = CreateUnit(Player(0), 'otbk', nx2_x - 200, nx2_y + 100, 0)
    set nx2_doc = CreateUnit(Player(0), 'odoc', nx2_x - 200, nx2_y - 100, 0)
    call SetUnitState(nx2_doc, UNIT_STATE_MANA, 500)
    loop
        exitwhen i >= 6
        set nx2_foe[i] = CreateUnit(Player(PLAYER_NEUTRAL_AGGRESSIVE), 'hfoo', nx2_x + 280 + 25 * i, nx2_y - 150 + 60 * i, 180)
        call PauseUnit(nx2_foe[i], true)
        set i = i + 1
    endloop
    // AI: a Neutral Hostile Mountain King, Paladin and mur'gul shadowcaster next to four player footmen
    set nx2_mk = CreateUnit(Player(PLAYER_NEUTRAL_AGGRESSIVE), 'Hmkg', nx2_x, nx2_y - 1200, 90)
    call SetHeroLevel(nx2_mk, 6, false)
    call SelectHeroSkill(nx2_mk, 'AHtc')
    call SelectHeroSkill(nx2_mk, 'AHtb')
    call SelectHeroSkill(nx2_mk, 'AHav')
    call SetUnitState(nx2_mk, UNIT_STATE_MANA, 1000)
    set nx2_pal = CreateUnit(Player(PLAYER_NEUTRAL_AGGRESSIVE), 'Hpal', nx2_x - 300, nx2_y - 1300, 90)
    call SetHeroLevel(nx2_pal, 3, false)
    call SelectHeroSkill(nx2_pal, 'AHhb')
    call SetUnitState(nx2_pal, UNIT_STATE_MANA, 1000)
    set nx2_hurt = CreateUnit(Player(PLAYER_NEUTRAL_AGGRESSIVE), 'hkni', nx2_x - 400, nx2_y - 1350, 90)
    call SetWidgetLife(nx2_hurt, 100)
    set nx2_caster = CreateUnit(Player(PLAYER_NEUTRAL_AGGRESSIVE), 'nmsc', nx2_x + 200, nx2_y - 1300, 90)
    set i = 0
    loop
        exitwhen i >= 4
        set nx2_bait[i] = CreateUnit(Player(0), 'hfoo', nx2_x - 60 + 40 * i, nx2_y - 1000, 270)
        call PauseUnit(nx2_bait[i], true)
        set i = i + 1
    endloop
    call TimerStart(CreateTimer(), 1.0, false, function nx2_first)
endfunction
