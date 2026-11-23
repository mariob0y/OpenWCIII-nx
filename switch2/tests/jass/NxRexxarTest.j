// warsmash-nx PC test (NX_DEBUG step "script:NxRexxarTest.j@nxr_main"): Rexxar's kit in a running campaign
// map: Attribute Bonus (Aamk), Summon Quilbeast level 2 + its Frenzy (Afzy) autocast, Stampede (Arsp).
// Every result is a line starting "NXTEST" on stderr.
native NxLog takes string s returns nothing

globals
    unit nxr_rex = null
    unit nxr_quil = null
    unit array nxr_foe
    real nxr_x = 0
    real nxr_y = 0
    integer nxr_agi0 = 0
endglobals

function nxr_b2s takes boolean b returns string
    if b then
        return "true"
    endif
    return "false"
endfunction

function nxr_find takes integer typeId returns unit
    local group g = CreateGroup()
    local unit u
    local unit found = null
    call GroupEnumUnitsOfPlayer(g, Player(0), null)
    loop
        set u = FirstOfGroup(g)
        exitwhen u == null
        if GetUnitTypeId(u) == typeId and GetWidgetLife(u) > 0.405 then
            set found = u
        endif
        call GroupRemoveUnit(g, u)
    endloop
    call DestroyGroup(g)
    return found
endfunction

function nxr_foeLives takes nothing returns string
    local integer i = 0
    local string s = ""
    loop
        exitwhen i >= 5
        set s = s + " " + I2S(R2I(GetWidgetLife(nxr_foe[i]))) + "@" + I2S(R2I(GetUnitX(nxr_foe[i]))) + "," + I2S(R2I(GetUnitY(nxr_foe[i])))
        set i = i + 1
    endloop
    return s
endfunction

function nxr_report takes nothing returns nothing
    call NxLog("NXTEST stampede: foe lives after 12 s of channel:" + nxr_foeLives() + " (started at 420 each)")
    call NxLog("NXTEST rexxar order now " + I2S(GetUnitCurrentOrder(nxr_rex)) + " mana " + R2S(GetUnitState(nxr_rex, UNIT_STATE_MANA)))
    call NxLog("NXTEST done")
endfunction

function nxr_frenzyCheck takes nothing returns nothing
    call NxLog("NXTEST frenzy buff on quilbeast (Bfzy level): " + I2S(GetUnitAbilityLevel(nxr_quil, 'Bfzy')))
    call NxLog("NXTEST order stampede: " + I2S(GetUnitCurrentOrder(nxr_rex)) + " -> " + nxr_b2s(IssuePointOrder(nxr_rex, "stampede", nxr_x + 600, nxr_y)))
    call TimerStart(CreateTimer(), 12.0, false, function nxr_report)
endfunction

function nxr_quilbeast takes nothing returns nothing
    set nxr_quil = nxr_find('nqb2')
    call NxLog("NXTEST quilbeast nqb2 found: " + nxr_b2s(nxr_quil != null) + " frenzy level " + I2S(GetUnitAbilityLevel(nxr_quil, 'Afzy')))
    call NxLog("NXTEST order frenzyon: " + nxr_b2s(IssueImmediateOrder(nxr_quil, "frenzyon")))
    call SetUnitPosition(nxr_quil, nxr_x + 300, nxr_y)
    call NxLog("NXTEST rexxar agility with Attribute Bonus level 2: " + I2S(GetHeroAgi(nxr_rex, true)) + " (was " + I2S(nxr_agi0) + ", expect +6)")
    call TimerStart(CreateTimer(), 4.0, false, function nxr_frenzyCheck)
endfunction

function nxr_main takes nothing returns nothing
    local location l = GetCameraTargetPositionLoc()
    local integer i = 0
    set nxr_x = GetLocationX(l)
    set nxr_y = GetLocationY(l)
    call NxLog("NXTEST rexxar test at " + R2S(nxr_x) + "," + R2S(nxr_y))
    set nxr_rex = CreateUnit(Player(0), 'Orex', nxr_x, nxr_y, 0)
    call SetHeroLevel(nxr_rex, 10, false)
    set nxr_agi0 = GetHeroAgi(nxr_rex, true)
    call SelectHeroSkill(nxr_rex, 'Arsq')
    call SelectHeroSkill(nxr_rex, 'Arsq')
    call SelectHeroSkill(nxr_rex, 'Arsp')
    call SelectHeroSkill(nxr_rex, 'Aamk')
    call SelectHeroSkill(nxr_rex, 'Aamk')
    call SetUnitState(nxr_rex, UNIT_STATE_MANA, 1000)
    call NxLog("NXTEST rexxar levels: quilbeast " + I2S(GetUnitAbilityLevel(nxr_rex, 'Arsq')) + " stampede " + I2S(GetUnitAbilityLevel(nxr_rex, 'Arsp')) + " attribute bonus " + I2S(GetUnitAbilityLevel(nxr_rex, 'Aamk')) + " sp left " + I2S(GetHeroSkillPoints(nxr_rex)))
    loop
        exitwhen i >= 5
        set nxr_foe[i] = CreateUnit(Player(PLAYER_NEUTRAL_AGGRESSIVE), 'hfoo', nxr_x + 550 + 40 * i, nxr_y - 100 + 50 * i, 180)
        call PauseUnit(nxr_foe[i], true)
        set i = i + 1
    endloop
    call NxLog("NXTEST order summon quilbeast: " + nxr_b2s(IssueImmediateOrder(nxr_rex, "summonquillbeast")))
    call TimerStart(CreateTimer(), 2.0, false, function nxr_quilbeast)
endfunction
