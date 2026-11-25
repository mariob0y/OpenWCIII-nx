// warsmash-nx PC test (NX_DEBUG step "script:NxItemTest.j@nxi_main"): the Durotar item abilities
// (NxItems): instant scrolls/potions/runes, cooldown groups, charges, power-ups, an orb letting a melee
// hero hit air, targeted items (Crown of the Deathlord, Wand of Illusion), Ancestral Staff, Town Portal,
// Tome of Retraining, Glyph of Omniscience, Potion of Invisibility.
// Every result is a line starting "NXTEST" on stderr.
native NxLog takes string s returns nothing

globals
    unit nxi_a = null
    unit nxi_b = null
    unit nxi_c = null
    unit nxi_foe = null
    unit nxi_harpy = null
    unit nxi_hall = null
    real nxi_x = 0
    real nxi_y = 0
    real nxi_harpylife = 0
    real nxi_foelife = 0
endglobals

function nxi_b2s takes boolean b returns string
    if b then
        return "true"
    endif
    return "false"
endfunction

function nxi_life takes unit u returns string
    return I2S(R2I(GetWidgetLife(u)))
endfunction

// the inventory slot holding an item of this type, or -1
function nxi_slot takes unit u, integer id returns integer
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

function nxi_give takes unit u, integer id returns integer
    call UnitAddItemById(u, id)
    return nxi_slot(u, id)
endfunction

function nxi_count takes player p, integer typeId returns integer
    local group g = CreateGroup()
    local integer n = 0
    local unit u
    call GroupEnumUnitsOfPlayer(g, p, null)
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

function nxi_end takes nothing returns nothing
    local real d = SquareRoot((GetUnitX(nxi_c) - GetUnitX(nxi_hall)) * (GetUnitX(nxi_c) - GetUnitX(nxi_hall)) + (GetUnitY(nxi_c) - GetUnitY(nxi_hall)) * (GetUnitY(nxi_c) - GetUnitY(nxi_hall)))
    call NxLog("NXTEST town portal: hero to great hall distance " + I2S(R2I(d)) + " (expect < 600), scroll left " + I2S(nxi_slot(nxi_c, 'stwp')))
    call NxLog("NXTEST orb: harpy life " + nxi_life(nxi_harpy) + " (was " + I2S(R2I(nxi_harpylife)) + ", melee Cairne with Orb of Fire hits air)")
    call NxLog("NXTEST done")
endfunction

function nxi_later takes nothing returns nothing
    local integer s
    call NxLog("NXTEST crown: footman life " + nxi_life(nxi_foe) + " (was " + I2S(R2I(nxi_foelife)) + ", Finger of Death 250)")
    call NxLog("NXTEST wand of illusion: Thralls of player 0 " + I2S(nxi_count(Player(0), 'Othr')) + " (expect 2), wand charges " + I2S(GetItemCharges(UnitItemInSlot(nxi_c, nxi_slot(nxi_c, 'will')))) + " (expect 2)")
    call NxLog("NXTEST ancestral staff: troll berserkers " + I2S(nxi_count(Player(0), 'otbk')) + " (expect 2)")
    // Town Portal: 5 s channel, then to the great hall
    set s = nxi_give(nxi_c, 'stwp')
    call NxLog("NXTEST order town portal: " + nxi_b2s(IssuePointOrderById(nxi_c, 852008 + s, GetUnitX(nxi_hall), GetUnitY(nxi_hall))))
    call TimerStart(CreateTimer(), 9.0, false, function nxi_end)
endfunction

function nxi_staff takes nothing returns nothing
    local integer s = nxi_give(nxi_c, 'axas')
    call NxLog("NXTEST order ancestral staff: " + nxi_b2s(IssueImmediateOrderById(nxi_c, 852008 + s)))
    call TimerStart(CreateTimer(), 3.0, false, function nxi_later)
endfunction

function nxi_wand takes nothing returns nothing
    // Wand of Illusion on the hero itself (3 charges)
    local integer s = nxi_give(nxi_c, 'will')
    call NxLog("NXTEST wand charges before " + I2S(GetItemCharges(UnitItemInSlot(nxi_c, s))))
    call NxLog("NXTEST harpy life now " + nxi_life(nxi_harpy) + " (was " + I2S(R2I(nxi_harpylife)) + ", only Cairne can reach it so far)")
    call NxLog("NXTEST order wand of illusion: " + nxi_b2s(IssueTargetOrderById(nxi_c, 852008 + s, nxi_c)))
    call TimerStart(CreateTimer(), 2.0, false, function nxi_staff)
endfunction

function nxi_targeted takes nothing returns nothing
    // Crown of the Deathlord on an enemy footman
    local integer s = nxi_give(nxi_c, 'crdt')
    set nxi_foelife = GetWidgetLife(nxi_foe)
    call NxLog("NXTEST footman visible to player 0 " + nxi_b2s(IsUnitVisible(nxi_foe, Player(0))) + " enemy " + nxi_b2s(IsUnitEnemy(nxi_foe, Player(0))) + " paused " + nxi_b2s(IsUnitPaused(nxi_foe)))
    call NxLog("NXTEST order crown (slot " + I2S(s) + "): " + nxi_b2s(IssueTargetOrderById(nxi_c, 852008 + s, nxi_foe)))
    call TimerStart(CreateTimer(), 2.0, false, function nxi_wand)
endfunction

function nxi_retrain takes nothing returns nothing
    local integer s
    call NxLog("NXTEST glyph: far point visible after " + nxi_b2s(IsVisibleToPlayer(GetRectMaxX(GetPlayableMapRect()) - 300, GetRectMaxY(GetPlayableMapRect()) - 300, Player(0))))
    // Tome of Retraining: the learned skill goes, its point comes back
    call SetHeroLevel(nxi_a, 3, false)
    call SelectHeroSkill(nxi_a, 'AOsh')
    call SelectHeroSkill(nxi_a, 'AOsh')
    call NxLog("NXTEST before retraining: shockwave level " + I2S(GetUnitAbilityLevel(nxi_a, 'AOsh')) + " skill points " + I2S(GetHeroSkillPoints(nxi_a)))
    set s = nxi_give(nxi_a, 'tret')
    call IssueImmediateOrderById(nxi_a, 852008 + s)
    call NxLog("NXTEST after retraining: shockwave level " + I2S(GetUnitAbilityLevel(nxi_a, 'AOsh')) + " skill points " + I2S(GetHeroSkillPoints(nxi_a)) + " (expect 0 and 3)")
    call nxi_targeted()
endfunction

function nxi_instant takes nothing returns nothing
    local integer s
    local real before
    local item it
    // Scroll of Healing (instant, uses the item, starts the AIha cooldown group)
    call SetWidgetLife(nxi_a, 200)
    set s = nxi_give(nxi_a, 'shea')
    call NxLog("NXTEST order healing scroll: " + nxi_b2s(IssueImmediateOrderById(nxi_a, 852008 + s)) + " life 200 -> " + nxi_life(nxi_a) + " (expect 350), scroll gone " + nxi_b2s(UnitItemInSlot(nxi_a, s) == null))
    // a second scroll during the cooldown: no effect, kept
    set s = nxi_give(nxi_a, 'shea')
    set before = GetWidgetLife(nxi_a)
    call IssueImmediateOrderById(nxi_a, 852008 + s)
    call NxLog("NXTEST second healing scroll on cooldown: life " + I2S(R2I(before)) + " -> " + nxi_life(nxi_a) + " (expect same), kept " + nxi_b2s(nxi_slot(nxi_a, 'shea') >= 0))
    // Rune of Lesser Healing: a power-up, works through the cooldown, then gone
    set before = GetWidgetLife(nxi_a)
    set it = CreateItem('rhe1', GetUnitX(nxi_a), GetUnitY(nxi_a))
    call UnitAddItem(nxi_a, it)
    call NxLog("NXTEST max life " + I2S(R2I(GetUnitState(nxi_a, UNIT_STATE_MAX_LIFE))))
    call NxLog("NXTEST rune of healing: life " + I2S(R2I(before)) + " -> " + nxi_life(nxi_a) + " (expect +125), rune in inventory " + nxi_b2s(nxi_slot(nxi_a, 'rhe1') >= 0))
    // Scroll of Mana
    call SetUnitState(nxi_a, UNIT_STATE_MANA, 0)
    set s = nxi_give(nxi_a, 'sman')
    call IssueImmediateOrderById(nxi_a, 852008 + s)
    call NxLog("NXTEST mana scroll: mana 0 -> " + I2S(R2I(GetUnitState(nxi_a, UNIT_STATE_MANA))) + " (expect 100)")
    // Scroll of Speed
    set before = GetUnitMoveSpeed(nxi_a)
    set s = nxi_give(nxi_a, 'shas')
    call IssueImmediateOrderById(nxi_a, 852008 + s)
    call NxLog("NXTEST speed scroll: speed " + R2S(before) + " -> " + R2S(GetUnitMoveSpeed(nxi_a)) + " buff Bspe " + I2S(GetUnitAbilityLevel(nxi_a, 'Bspe')))
    // Scroll of Protection
    set s = nxi_give(nxi_a, 'spro')
    call IssueImmediateOrderById(nxi_a, 852008 + s)
    call NxLog("NXTEST protection scroll: buff Bdef " + I2S(GetUnitAbilityLevel(nxi_a, 'Bdef')))
    // Potion of Lesser Invulnerability: damage does nothing
    set s = nxi_give(nxi_a, 'pnvl')
    call IssueImmediateOrderById(nxi_a, 852008 + s)
    set before = GetWidgetLife(nxi_a)
    call UnitDamageTarget(nxi_foe, nxi_a, 100, false, false, ATTACK_TYPE_NORMAL, DAMAGE_TYPE_NORMAL, WEAPON_TYPE_WHOKNOWS)
    call NxLog("NXTEST invulnerability potion: buff Bvul " + I2S(GetUnitAbilityLevel(nxi_a, 'Bvul')) + " life after 100 damage " + I2S(R2I(before)) + " -> " + nxi_life(nxi_a) + " (expect same)")
    // Potion of Invisibility
    set s = nxi_give(nxi_b, 'pinv')
    call IssueImmediateOrderById(nxi_b, 852008 + s)
    call NxLog("NXTEST invisibility potion: invisible to neutral hostile " + nxi_b2s(IsUnitInvisible(nxi_b, Player(PLAYER_NEUTRAL_AGGRESSIVE))) + " buff Binv " + I2S(GetUnitAbilityLevel(nxi_b, 'Binv')))
    // Glyph of Omniscience (power-up): the far corner of the map becomes visible
    call NxLog("NXTEST glyph: far point " + R2S(GetRectMaxX(GetPlayableMapRect()) - 300) + "," + R2S(GetRectMaxY(GetPlayableMapRect()) - 300) + " visible before " + nxi_b2s(IsVisibleToPlayer(GetRectMaxX(GetPlayableMapRect()) - 300, GetRectMaxY(GetPlayableMapRect()) - 300, Player(0))))
    set it = CreateItem('gomn', GetUnitX(nxi_a), GetUnitY(nxi_a))
    call UnitAddItem(nxi_a, it)
    call TimerStart(CreateTimer(), 2.0, false, function nxi_retrain)
endfunction

function nxi_orb takes nothing returns nothing
    set nxi_harpylife = GetWidgetLife(nxi_harpy)
    call NxLog("NXTEST harpy visible " + nxi_b2s(IsUnitVisible(nxi_harpy, Player(0))) + " enemy " + nxi_b2s(IsUnitEnemy(nxi_harpy, Player(0))))
    call NxLog("NXTEST melee Cairne attacks the harpy without an orb: " + nxi_b2s(IssueTargetOrder(nxi_b, "attack", nxi_harpy)))
    call UnitAddItemById(nxi_b, 'ofir')
    call NxLog("NXTEST with Orb of Fire: " + nxi_b2s(IssueTargetOrder(nxi_b, "attack", nxi_harpy)))
endfunction

function nxi_start takes nothing returns nothing
    call nxi_orb()
    call nxi_instant()
endfunction

function nxi_main takes nothing returns nothing
    local location l = GetCameraTargetPositionLoc()
    set nxi_x = GetLocationX(l)
    set nxi_y = GetLocationY(l)
    call NxLog("NXTEST item test at " + R2S(nxi_x) + "," + R2S(nxi_y))
    set nxi_a = CreateUnit(Player(0), 'Otch', nxi_x, nxi_y, 0)
    set nxi_b = CreateUnit(Player(0), 'Ocbh', nxi_x + 300, nxi_y - 300, 0)
    set nxi_c = CreateUnit(Player(0), 'Othr', nxi_x - 300, nxi_y - 300, 0)
    set nxi_foe = CreateUnit(Player(PLAYER_NEUTRAL_AGGRESSIVE), 'hfoo', nxi_x - 300, nxi_y + 400, 270)
    set nxi_harpy = CreateUnit(Player(PLAYER_NEUTRAL_AGGRESSIVE), 'nhar', nxi_x + 500, nxi_y - 300, 180)
    set nxi_hall = CreateUnit(Player(0), 'ogre', nxi_x + 1800, nxi_y + 1200, 270)
    call SetUnitAcquireRange(nxi_foe, 0)
    call SetUnitAcquireRange(nxi_harpy, 0)
    call SetCameraPosition(nxi_x, nxi_y)
    // fog of war needs a moment to see the new units
    call TimerStart(CreateTimer(), 2.0, false, function nxi_start)
endfunction
