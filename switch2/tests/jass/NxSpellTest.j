// warsmash-nx PC test (NX_DEBUG step "script:NxSpellTest.j@nxt_main"): hero spells, game cache and hero
// natives inside a running campaign map. Every result is a line starting "NXTEST" on stderr.
native NxLog takes string s returns nothing

globals
    unit nxt_rex = null
    unit nxt_rok = null
    unit nxt_foe = null
    unit nxt_ally1 = null
    unit nxt_ally2 = null
    real nxt_x = 0
    real nxt_y = 0
    real nxt_foeLife = 0
endglobals

function nxt_b2s takes boolean b returns string
    if b then
        return "true"
    endif
    return "false"
endfunction

function nxt_count takes integer typeId returns integer
    local group g = CreateGroup()
    local integer n = 0
    local unit u
    call GroupEnumUnitsOfPlayer(g, Player(0), null)
    loop
        set u = FirstOfGroup(g)
        exitwhen u == null
        if GetUnitTypeId(u) == typeId and GetWidgetLife(u) > 0.405 then
            set n = n + 1
        endif
        call GroupRemoveUnit(g, u)
    endloop
    call DestroyGroup(g)
    return n
endfunction

function nxt_report takes nothing returns nothing
    call NxLog("NXTEST misha (ngzc) alive 12s after casting: " + I2S(nxt_count('ngzc')) + " quilbeasts " + I2S(nxt_count('nqb1')))
    call NxLog("NXTEST storm bolt: footman life " + R2S(nxt_foeLife) + " -> " + R2S(GetWidgetLife(nxt_foe)))
    call NxLog("NXTEST healing wave: grunt1 " + R2S(GetWidgetLife(nxt_ally1)) + " grunt2 " + R2S(GetWidgetLife(nxt_ally2)) + " (both started at 100)")
    call NxLog("NXTEST serpent wards (osp1) alive: " + I2S(nxt_count('osp1')))
    call NxLog("NXTEST rokhan mana " + R2S(GetUnitState(nxt_rok, UNIT_STATE_MANA)) + " rexxar mana " + R2S(GetUnitState(nxt_rex, UNIT_STATE_MANA)))
    call NxLog("NXTEST done")
endfunction

function nxt_castWard takes nothing returns nothing
    call NxLog("NXTEST rokhan levels: healingwave " + I2S(GetUnitAbilityLevel(nxt_rok, 'ANhw')) + " serpentward " + I2S(GetUnitAbilityLevel(nxt_rok, 'Arsw')) + " sp " + I2S(GetHeroSkillPoints(nxt_rok)) + " order now " + I2S(GetUnitCurrentOrder(nxt_rok)))
    call NxLog("NXTEST order ward: " + nxt_b2s(IssuePointOrder(nxt_rok, "ward", nxt_x + 150, nxt_y - 150)))
    call NxLog("NXTEST order ward by id: " + nxt_b2s(IssuePointOrderById(nxt_rok, 852504, nxt_x + 150, nxt_y - 150)))
    call TimerStart(CreateTimer(), 8.0, false, function nxt_report)
endfunction

function nxt_castBolts takes nothing returns nothing
    set nxt_foeLife = GetWidgetLife(nxt_foe)
    call NxLog("NXTEST order thunderbolt: " + nxt_b2s(IssueTargetOrder(nxt_rex, "thunderbolt", nxt_foe)))
    call NxLog("NXTEST order healingwave: " + nxt_b2s(IssueTargetOrder(nxt_rok, "healingwave", nxt_ally1)))
    call TimerStart(CreateTimer(), 3.0, false, function nxt_castWard)
endfunction

function nxt_castMisha takes nothing returns nothing
    call NxLog("NXTEST order spiritwolf (Summon Misha): " + nxt_b2s(IssueImmediateOrder(nxt_rex, "spiritwolf")))
    call TimerStart(CreateTimer(), 2.0, false, function nxt_castBolts)
endfunction

function nxt_main takes nothing returns nothing
    local location l = GetCameraTargetPositionLoc()
    local gamecache gc
    local unit copy
    set nxt_x = GetLocationX(l)
    set nxt_y = GetLocationY(l)
    call NxLog("NXTEST start at " + R2S(nxt_x) + "," + R2S(nxt_y))

    set nxt_rex = CreateUnit(Player(0), 'Orex', nxt_x, nxt_y, 0)
    call SetHeroLevel(nxt_rex, 10, false)
    call SelectHeroSkill(nxt_rex, 'Arsg')
    call SelectHeroSkill(nxt_rex, 'ANsb')
    call SelectHeroSkill(nxt_rex, 'ANsb')
    call SelectHeroSkill(nxt_rex, 'Arsq')
    call SetUnitState(nxt_rex, UNIT_STATE_MANA, 1000)
    call NxLog("NXTEST rexxar level " + I2S(GetHeroLevel(nxt_rex)) + " skill points left " + I2S(GetHeroSkillPoints(nxt_rex)) + " misha " + I2S(GetUnitAbilityLevel(nxt_rex, 'Arsg')) + " stormbolt " + I2S(GetUnitAbilityLevel(nxt_rex, 'ANsb')))

    set nxt_rok = CreateUnit(Player(0), 'Orkn', nxt_x - 100, nxt_y, 0)
    call SetHeroLevel(nxt_rok, 10, false)
    call SelectHeroSkill(nxt_rok, 'ANhw')
    call SelectHeroSkill(nxt_rok, 'Arsw')
    call SetUnitState(nxt_rok, UNIT_STATE_MANA, 1000)

    set nxt_ally1 = CreateUnit(Player(0), 'ogru', nxt_x - 250, nxt_y + 100, 0)
    set nxt_ally2 = CreateUnit(Player(0), 'ogru', nxt_x - 300, nxt_y + 150, 0)
    call SetUnitState(nxt_ally1, UNIT_STATE_LIFE, 100)
    call SetUnitState(nxt_ally2, UNIT_STATE_LIFE, 100)
    set nxt_foe = CreateUnit(Player(PLAYER_NEUTRAL_AGGRESSIVE), 'hfoo', nxt_x + 350, nxt_y, 180)
    call PauseUnit(nxt_foe, true)

    // game cache round trip
    call UnitAddItemById(nxt_rex, 'phea')
    set gc = InitGameCache("NxTest.w3v")
    call StoreInteger(gc, "m", "answer", 42)
    call StoreBoolean(gc, "m", "flag", true)
    call StoreReal(gc, "m", "pi", 3.25)
    call StoreString(gc, "m", "name", "Rexxar")
    call NxLog("NXTEST store unit: " + nxt_b2s(StoreUnit(gc, "m", "hero", nxt_rex)))
    call NxLog("NXTEST stored int " + I2S(GetStoredInteger(gc, "M", "ANSWER")) + " bool " + nxt_b2s(GetStoredBoolean(gc, "m", "flag")) + " real " + R2S(GetStoredReal(gc, "m", "pi")) + " string " + GetStoredString(gc, "m", "name") + " have unit " + nxt_b2s(HaveStoredUnit(gc, "m", "hero")) + " have missing " + nxt_b2s(HaveStoredInteger(gc, "m", "nope")))
    call NxLog("NXTEST save cache: " + nxt_b2s(SaveGameCache(gc)))
    call ReloadGameCachesFromDisk()
    set gc = InitGameCache("NxTest.w3v")
    set copy = RestoreUnit(gc, "m", "hero", Player(0), nxt_x + 100, nxt_y + 300, 0)
    call NxLog("NXTEST restored after reload: type " + nxt_b2s(GetUnitTypeId(copy) == 'Orex') + " level " + I2S(GetHeroLevel(copy)) + " stormbolt " + I2S(GetUnitAbilityLevel(copy, 'ANsb')) + " item0 " + nxt_b2s(GetItemTypeId(UnitItemInSlot(copy, 0)) == 'phea') + " int " + I2S(GetStoredInteger(gc, "m", "answer")))
    call FlushStoredMission(gc, "m")
    call NxLog("NXTEST after flush have int: " + nxt_b2s(HaveStoredInteger(gc, "m", "answer")))

    // revive
    call KillUnit(copy)
    call NxLog("NXTEST killed copy, alive " + nxt_b2s(GetWidgetLife(copy) > 0.405))
    call NxLog("NXTEST revive: " + nxt_b2s(ReviveHero(copy, nxt_x + 100, nxt_y + 300, false)) + " alive " + nxt_b2s(GetWidgetLife(copy) > 0.405) + " life " + R2S(GetWidgetLife(copy)))
    call RemoveUnit(copy)

    call NxLog("NXTEST hero natives: str " + I2S(GetHeroStr(nxt_rex, true)) + " xp " + I2S(GetHeroXP(nxt_rex)) + " proper name " + GetHeroProperName(nxt_rex) + " isHeroId " + nxt_b2s(IsHeroUnitId('Orex')) + "/" + nxt_b2s(IsHeroUnitId('ogru')) + " visible " + nxt_b2s(IsLocationVisibleToPlayer(l, Player(0))))
    call TimerStart(CreateTimer(), 1.0, false, function nxt_castMisha)
endfunction
