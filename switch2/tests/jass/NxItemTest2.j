// warsmash-nx PC test (NX_DEBUG step "script:NxItemTest2.j@nxj_main"): the remaining active Durotar items
// (NxItems): restoration scroll/potion, anti-magic and vampiric potions, Rune of Rebirth, Mechanical
// Critter, Wand of Negation, Wand of Neutralization, Wand of Mana Stealing, Spider Silk Broach, Staff of
// Sanctuary, Amulet of Recall, Scroll of the Unholy Legion, Ivory Tower, Horn of the Clouds, Scepter of the
// Sea and Gloves of Spell Mastery (Control Magic). Every result is a line starting "NXTEST" on stderr.
native NxLog takes string s returns nothing

globals
    unit nxj_a = null
    unit nxj_c = null
    unit nxj_d = null
    unit nxj_e = null
    unit nxj_grunt = null
    unit nxj_priest = null
    unit nxj_harpy = null
    unit nxj_tower = null
    unit nxj_hall = null
    unit nxj_creep = null
    unit array nxj_far
    real nxj_x = 0
    real nxj_y = 0
    integer nxj_n = 0
endglobals

function nxj_b2s takes boolean b returns string
    if b then
        return "true"
    endif
    return "false"
endfunction

function nxj_life takes unit u returns string
    return I2S(R2I(GetWidgetLife(u)))
endfunction

function nxj_slot takes unit u, integer id returns integer
    local integer i = 0
    loop
        exitwhen i >= 6
        if GetItemTypeId(UnitItemInSlot(u, i)) == id then
            return i
        endif
        set i = i + 1
    endloop
    return -1
endfunction

function nxj_give takes unit u, integer id returns integer
    call UnitAddItemById(u, id)
    return nxj_slot(u, id)
endfunction

function nxj_count takes player p, integer typeId returns integer
    local group g = CreateGroup()
    local integer n = 0
    local unit u
    call GroupEnumUnitsOfPlayer(g, p, null)
    loop
        set u = FirstOfGroup(g)
        exitwhen u == null
        if (typeId == 0 or GetUnitTypeId(u) == typeId) and GetWidgetLife(u) > 0.405 then
            set n = n + 1
        endif
        call GroupRemoveUnit(g, u)
    endloop
    call DestroyGroup(g)
    return n
endfunction

function nxj_dist takes unit a, unit b returns integer
    return R2I(SquareRoot((GetUnitX(a) - GetUnitX(b)) * (GetUnitX(a) - GetUnitX(b)) + (GetUnitY(a) - GetUnitY(b)) * (GetUnitY(a) - GetUnitY(b))))
endfunction

function nxj_end takes nothing returns nothing
    call NxLog("NXTEST sanctuary later: grunt life " + nxj_life(nxj_grunt) + " (healing)")
    call NxLog("NXTEST control magic: player 0 sea elementals " + I2S(nxj_count(Player(0), 'nmrr')) + " (expect 1), player 1 " + I2S(nxj_count(Player(1), 'nmrr')) + " (expect 1), thrall mana " + I2S(R2I(GetUnitState(nxj_c, UNIT_STATE_MANA))))
    call NxLog("NXTEST done")
endfunction

function nxj_control takes nothing returns nothing
    local group g = CreateGroup()
    local unit u
    local unit target = null
    local integer s
    call NxLog("NXTEST scepter of the sea: player 1 sea elementals " + I2S(nxj_count(Player(1), 'nmrr')) + " (expect 2)")
    call GroupEnumUnitsOfPlayer(g, Player(1), null)
    loop
        set u = FirstOfGroup(g)
        exitwhen u == null
        if GetUnitTypeId(u) == 'nmrr' then
            set target = u
        endif
        call GroupRemoveUnit(g, u)
    endloop
    call DestroyGroup(g)
    set s = nxj_give(nxj_c, 'gvsm')
    call SetUnitState(nxj_c, UNIT_STATE_MANA, 1000)
    call NxLog("NXTEST control magic: elemental life " + nxj_life(target) + " thrall mana before " + I2S(R2I(GetUnitState(nxj_c, UNIT_STATE_MANA))) + " (cost 25 + 0.3 per life)")
    call NxLog("NXTEST control magic target visible " + nxj_b2s(IsUnitVisible(target, Player(0))) + " order: " + nxj_b2s(IssueTargetOrderById(nxj_c, 852008 + s, target)))
    call TimerStart(CreateTimer(), 3.0, false, function nxj_end)
endfunction

function nxj_summons takes nothing returns nothing
    local integer s
    call NxLog("NXTEST ivory tower: player 0 scout towers " + I2S(nxj_count(Player(0), 'hwtw')) + " (expect 1)")
    call NxLog("NXTEST horn of the clouds: tower buff Bclf " + I2S(GetUnitAbilityLevel(nxj_tower, 'Bclf')))
    call NxLog("NXTEST sanctuary: grunt to great hall " + I2S(nxj_dist(nxj_grunt, nxj_hall)) + " buff BNsa " + I2S(GetUnitAbilityLevel(nxj_grunt, 'BNsa')) + " life " + nxj_life(nxj_grunt))
    // a hostile hero summons with the Scepter of the Sea
    set s = nxj_give(nxj_e, 'rots')
    call NxLog("NXTEST player 1 scepter order: " + nxj_b2s(IssueImmediateOrderById(nxj_e, 852008 + s)))
    call TimerStart(CreateTimer(), 2.0, false, function nxj_control)
endfunction

function nxj_fog takes nothing returns nothing
    local integer s
    call NxLog("NXTEST unholy legion: player 0 footmen " + I2S(nxj_count(Player(0), 'hfoo')) + " (expect 4 more: the three footmen and the rune creep)")
    set s = nxj_give(nxj_a, 'sfog')
    call NxLog("NXTEST order horn of the clouds: " + nxj_b2s(IssuePointOrderById(nxj_a, 852008 + s, GetUnitX(nxj_tower), GetUnitY(nxj_tower))))
    // the hero walks into cast range of the tower first
    call TimerStart(CreateTimer(), 5.0, false, function nxj_summons)
endfunction

function nxj_legion takes nothing returns nothing
    local integer s
    call NxLog("NXTEST recall: far grunts to thrall " + I2S(nxj_dist(nxj_far[0], nxj_c)) + " " + I2S(nxj_dist(nxj_far[1], nxj_c)) + " " + I2S(nxj_dist(nxj_far[2], nxj_c)) + " (expect < 400)")
    call NxLog("NXTEST before unholy legion: player 0 footmen " + I2S(nxj_count(Player(0), 'hfoo')))
    // a hostile guard tower for the Horn of the Clouds, made late: it shoots nearby units and draws them off
    set nxj_tower = CreateUnit(Player(PLAYER_NEUTRAL_AGGRESSIVE), 'hgtw', nxj_x + 500, nxj_y - 500, 0)
    set s = nxj_give(nxj_c, 'scul')
    call NxLog("NXTEST order unholy legion: " + nxj_b2s(IssueImmediateOrderById(nxj_c, 852008 + s)))
    set s = nxj_give(nxj_d, 'tsct')
    call NxLog("NXTEST order ivory tower: " + nxj_b2s(IssuePointOrderById(nxj_d, 852008 + s, GetUnitX(nxj_d) - 200, GetUnitY(nxj_d) + 200)))
    call TimerStart(CreateTimer(), 2.0, false, function nxj_fog)
endfunction

function nxj_points takes nothing returns nothing
    local integer s
    local integer i = 0
    call NxLog("NXTEST mana steal: thrall mana " + I2S(R2I(GetUnitState(nxj_c, UNIT_STATE_MANA))) + " (expect 50), priest " + I2S(R2I(GetUnitState(nxj_priest, UNIT_STATE_MANA))) + " (expect 150)")
    call NxLog("NXTEST web: harpy buffs Bwea " + I2S(GetUnitAbilityLevel(nxj_harpy, 'Bwea')) + " Bweb " + I2S(GetUnitAbilityLevel(nxj_harpy, 'Bweb')) + ", hero " + I2S(nxj_dist(nxj_a, nxj_harpy)) + " from the harpy, order " + OrderId2String(GetUnitCurrentOrder(nxj_a)) + ", harpy life " + nxj_life(nxj_harpy))
    // Staff of Sanctuary on the hurt grunt, the Amulet of Recall at the far grunts
    call SetWidgetLife(nxj_grunt, 100)
    set s = nxj_give(nxj_a, 'ssan')
    call NxLog("NXTEST order sanctuary: " + nxj_b2s(IssueTargetOrderById(nxj_a, 852008 + s, nxj_grunt)))
    set s = nxj_give(nxj_c, 'amrc')
    call NxLog("NXTEST order recall: " + nxj_b2s(IssuePointOrderById(nxj_c, 852008 + s, GetUnitX(nxj_far[0]), GetUnitY(nxj_far[0]))))
    // corpses for the Unholy Legion
    loop
        exitwhen i >= 3
        call KillUnit(CreateUnit(Player(PLAYER_NEUTRAL_AGGRESSIVE), 'hfoo', GetUnitX(nxj_c) + 150 + i * 60, GetUnitY(nxj_c) + 250, 0))
        set i = i + 1
    endloop
    call TimerStart(CreateTimer(), 3.0, false, function nxj_legion)
endfunction

function nxj_steal takes nothing returns nothing
    local integer s
    call NxLog("NXTEST after neutralization: hero Bdef " + I2S(GetUnitAbilityLevel(nxj_a, 'Bdef')) + " grunt Bdef " + I2S(GetUnitAbilityLevel(nxj_grunt, 'Bdef')) + " (expect 0 0), wand charges " + I2S(GetItemCharges(UnitItemInSlot(nxj_c, nxj_slot(nxj_c, 'wneu')))) + " (expect 3)")
    // Wand of Mana Stealing on the priest, Spider Silk Broach on the harpy
    call SetUnitState(nxj_c, UNIT_STATE_MANA, 0)
    set s = nxj_give(nxj_c, 'woms')
    call NxLog("NXTEST order mana steal: " + nxj_b2s(IssueTargetOrderById(nxj_c, 852008 + s, nxj_priest)))
    set s = nxj_give(nxj_a, 'silk')
    call NxLog("NXTEST order web: " + nxj_b2s(IssueTargetOrderById(nxj_a, 852008 + s, nxj_harpy)))
    call TimerStart(CreateTimer(), 3.0, false, function nxj_points)
endfunction

function nxj_wands2 takes nothing returns nothing
    local integer s
    call NxLog("NXTEST after negation: hero Bdef " + I2S(GetUnitAbilityLevel(nxj_a, 'Bdef')) + " grunt Bdef " + I2S(GetUnitAbilityLevel(nxj_grunt, 'Bdef')) + " hero BIpv " + I2S(GetUnitAbilityLevel(nxj_a, 'BIpv')) + " (expect 0 0 0), grunt " + I2S(nxj_dist(nxj_a, nxj_grunt)) + " from the hero, order " + OrderId2String(GetUnitCurrentOrder(nxj_grunt)))
    // protection again, then Wand of Neutralization on the hero: it jumps to the grunt
    call SetUnitPosition(nxj_grunt, GetUnitX(nxj_a) + 100, GetUnitY(nxj_a))
    set s = nxj_give(nxj_a, 'spro')
    call IssueImmediateOrderById(nxj_a, 852008 + s)
    call NxLog("NXTEST protection again: hero Bdef " + I2S(GetUnitAbilityLevel(nxj_a, 'Bdef')) + " grunt Bdef " + I2S(GetUnitAbilityLevel(nxj_grunt, 'Bdef')))
    set s = nxj_give(nxj_c, 'wneu')
    call NxLog("NXTEST order neutralization: " + nxj_b2s(IssueTargetOrderById(nxj_c, 852008 + s, nxj_a)))
    call TimerStart(CreateTimer(), 2.0, false, function nxj_steal)
endfunction

function nxj_wands takes nothing returns nothing
    local integer s
    // protection on the hero and the grunt next to it
    call SetUnitPosition(nxj_grunt, GetUnitX(nxj_a) + 100, GetUnitY(nxj_a))
    set s = nxj_give(nxj_a, 'spro')
    call IssueImmediateOrderById(nxj_a, 852008 + s)
    call NxLog("NXTEST rune of rebirth 2 s later: player 0 footmen " + I2S(nxj_count(Player(0), 'hfoo')) + " (expect 1)")
    call NxLog("NXTEST protection: hero Bdef " + I2S(GetUnitAbilityLevel(nxj_a, 'Bdef')) + " grunt Bdef " + I2S(GetUnitAbilityLevel(nxj_grunt, 'Bdef')) + " (grunt " + I2S(nxj_dist(nxj_a, nxj_grunt)) + " away, area 600)")
    set s = nxj_give(nxj_c, 'wneg')
    call NxLog("NXTEST order negation: " + nxj_b2s(IssuePointOrderById(nxj_c, 852008 + s, GetUnitX(nxj_a), GetUnitY(nxj_a))))
    call TimerStart(CreateTimer(), 2.0, false, function nxj_wands2)
endfunction

function nxj_instant takes nothing returns nothing
    local integer s
    local integer before
    local item it
    // Scroll of Restoration: hero and the grunt beside it
    call SetWidgetLife(nxj_a, 200)
    call SetUnitState(nxj_a, UNIT_STATE_MANA, 0)
    call SetWidgetLife(nxj_grunt, 100)
    set s = nxj_give(nxj_a, 'sres')
    call IssueImmediateOrderById(nxj_a, 852008 + s)
    call NxLog("NXTEST restoration scroll: hero life 200 -> " + nxj_life(nxj_a) + " (expect 500) mana 0 -> " + I2S(R2I(GetUnitState(nxj_a, UNIT_STATE_MANA))) + " (expect 150), grunt 100 -> " + nxj_life(nxj_grunt))
    // Potion of Restoration
    call SetWidgetLife(nxj_a, 100)
    call SetUnitState(nxj_a, UNIT_STATE_MANA, 0)
    set s = nxj_give(nxj_a, 'pres')
    call IssueImmediateOrderById(nxj_a, 852008 + s)
    call NxLog("NXTEST restoration potion: life 100 -> " + nxj_life(nxj_a) + " (expect 600) mana 0 -> " + I2S(R2I(GetUnitState(nxj_a, UNIT_STATE_MANA))) + " (expect 200 or max)")
    // Anti-magic and Vampiric potions
    set s = nxj_give(nxj_a, 'pams')
    call IssueImmediateOrderById(nxj_a, 852008 + s)
    call NxLog("NXTEST anti-magic potion: Bams " + I2S(GetUnitAbilityLevel(nxj_a, 'Bams')))
    set s = nxj_give(nxj_a, 'vamp')
    call IssueImmediateOrderById(nxj_a, 852008 + s)
    call NxLog("NXTEST vampiric potion: BIpv " + I2S(GetUnitAbilityLevel(nxj_a, 'BIpv')))
    // Rune of Rebirth where a creep died
    set before = nxj_count(Player(0), 'hfoo')
    set it = CreateItem('rreb', GetUnitX(nxj_a), GetUnitY(nxj_a))
    call UnitAddItem(nxj_a, it)
    call NxLog("NXTEST rune of rebirth: player 0 footmen " + I2S(before) + " -> " + I2S(nxj_count(Player(0), 'hfoo')) + " (expect +1)")
    // Mechanical Critter: no unit in its base data
    set before = nxj_count(Player(0), 0)
    set s = nxj_give(nxj_a, 'mcri')
    call NxLog("NXTEST order mechanical critter: " + nxj_b2s(IssueImmediateOrderById(nxj_a, 852008 + s)))
    call TimerStart(CreateTimer(), 2.0, false, function nxj_wands)
endfunction

function nxj_main takes nothing returns nothing
    local location l = GetCameraTargetPositionLoc()
    local integer i = 0
    set nxj_x = GetLocationX(l)
    set nxj_y = GetLocationY(l)
    call NxLog("NXTEST item test 2 at " + R2S(nxj_x) + "," + R2S(nxj_y))
    call SetPlayerAlliance(Player(1), Player(0), ALLIANCE_PASSIVE, false)
    call SetPlayerAlliance(Player(0), Player(1), ALLIANCE_PASSIVE, false)
    set nxj_a = CreateUnit(Player(0), 'Otch', nxj_x, nxj_y, 0)
    call SetUnitAcquireRange(nxj_a, 0)
    set nxj_grunt = CreateUnit(Player(0), 'ogru', nxj_x + 120, nxj_y, 0)
    call SetUnitAcquireRange(nxj_grunt, 0)
    set nxj_c = CreateUnit(Player(0), 'Othr', nxj_x - 300, nxj_y - 300, 0)
    call SetUnitAcquireRange(nxj_c, 0)
    set nxj_e = CreateUnit(Player(1), 'Ocbh', nxj_x - 600, nxj_y - 300, 0)
    call SetUnitAcquireRange(nxj_e, 0)
    set nxj_priest = CreateUnit(Player(PLAYER_NEUTRAL_AGGRESSIVE), 'hmpr', nxj_x - 300, nxj_y + 400, 270)
    call SetUnitState(nxj_priest, UNIT_STATE_MANA, 200)
    call SetUnitAcquireRange(nxj_priest, 0)
    set nxj_harpy = CreateUnit(Player(PLAYER_NEUTRAL_AGGRESSIVE), 'nhar', nxj_x + 150, nxj_y + 250, 180)
    call SetUnitAcquireRange(nxj_harpy, 0)
    set nxj_creep = CreateUnit(Player(PLAYER_NEUTRAL_AGGRESSIVE), 'hfoo', nxj_x + 200, nxj_y + 150, 0)
    call SetUnitAcquireRange(nxj_creep, 0)
    // the creep that "dropped" the Rune of Rebirth dies before the rune is picked up
    call KillUnit(nxj_creep)
    set nxj_d = CreateUnit(Player(0), 'Obla', nxj_x + 700, nxj_y + 600, 0)
    call SetUnitAcquireRange(nxj_d, 0)
    set nxj_hall = CreateUnit(Player(0), 'ogre', nxj_x + 1800, nxj_y + 1200, 270)
    loop
        exitwhen i >= 3
        set nxj_far[i] = CreateUnit(Player(0), 'ogru', nxj_x - 1600 + i * 80, nxj_y - 1200, 0)
        set i = i + 1
    endloop
    call SetCameraPosition(nxj_x, nxj_y)
    // fog of war needs a moment to see the new units
    call TimerStart(CreateTimer(), 2.0, false, function nxj_instant)
endfunction
