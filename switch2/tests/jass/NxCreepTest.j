// warsmash-nx PC test (NX_DEBUG step "script:NxCreepTest.j@nxc_main"): the enemy and creep spells of NxSpells3.
// Phases run one after another at the camera; each makes its units, acts after 2 s (fog of war), logs and
// removes them. Every result is a line starting "NXTEST" on stderr.
native NxLog takes string s returns nothing

globals
    group nxc_g = null
    real nxc_x = 0
    real nxc_y = 0
    unit array nxc_u
    integer nxc_phase = 0
endglobals

function nxc_b2s takes boolean b returns string
    if b then
        return "true"
    endif
    return "false"
endfunction

function nxc_life takes unit u returns string
    return I2S(R2I(GetWidgetLife(u)))
endfunction

function nxc_mana takes unit u returns string
    return I2S(R2I(GetUnitState(u, UNIT_STATE_MANA)))
endfunction

function nxc_lvl takes unit u, integer id returns string
    return I2S(GetUnitAbilityLevel(u, id))
endfunction

// a phase unit: acquire range 0 so nothing starts fights by itself
function nxc_make takes integer i, integer p, integer id, real dx, real dy returns unit
    set nxc_u[i] = CreateUnit(Player(p), id, nxc_x + dx, nxc_y + dy, 0)
    call SetUnitAcquireRange(nxc_u[i], 0)
    call GroupAddUnit(nxc_g, nxc_u[i])
    return nxc_u[i]
endfunction

// a caster: the ability added, plenty of mana
function nxc_caster takes integer i, integer p, integer abil, real dx, real dy returns unit
    call nxc_make(i, p, 'oshm', dx, dy)
    // no Bloodlust autocast: it replaced the order under test when allies fought nearby
    call UnitRemoveAbility(nxc_u[i], 'Ablo')
    call UnitAddAbility(nxc_u[i], abil)
    call SetUnitState(nxc_u[i], UNIT_STATE_MAX_MANA, 2000)
    call SetUnitState(nxc_u[i], UNIT_STATE_MANA, 2000)
    return nxc_u[i]
endfunction

function nxc_count takes player p, integer typeId returns integer
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

function nxc_clear takes nothing returns nothing
    local unit u
    loop
        set u = FirstOfGroup(nxc_g)
        exitwhen u == null
        call GroupRemoveUnit(nxc_g, u)
        call RemoveUnit(u)
    endloop
endfunction

// removes every unit of a type a phase summoned
function nxc_clearType takes integer typeId returns nothing
    local group g = CreateGroup()
    local unit u
    call GroupEnumUnitsOfType(g, UnitId2String(typeId), null)
    loop
        set u = FirstOfGroup(g)
        exitwhen u == null
        call GroupRemoveUnit(g, u)
        if GetUnitTypeId(u) == typeId then
            call RemoveUnit(u)
        endif
    endloop
    call DestroyGroup(g)
endfunction

// ---------------------------------------------------------------- phase 1: Locust
function nxc_p1check takes nothing returns nothing
    local group g = CreateGroup()
    local integer inRange = 0
    local unit u
    call GroupEnumUnitsInRange(g, nxc_x + 100, nxc_y, 300, null)
    loop
        set u = FirstOfGroup(g)
        exitwhen u == null
        if u == nxc_u[1] then
            set inRange = inRange + 1
        endif
        call GroupRemoveUnit(g, u)
    endloop
    call DestroyGroup(g)
    call NxLog("NXTEST locust: in GroupEnumUnitsInRange " + I2S(inRange) + " (expect 0), player 1 dummies " + I2S(nxc_count(Player(1), 'uloc')) + " (expect 1), attack order " + nxc_b2s(IssueTargetOrder(nxc_u[0], "attack", nxc_u[1])) + " (expect false), life " + nxc_life(nxc_u[1]))
    call UnitDamageTarget(nxc_u[0], nxc_u[1], 100, true, false, ATTACK_TYPE_NORMAL, DAMAGE_TYPE_NORMAL, null)
    call NxLog("NXTEST locust after 100 damage: life " + nxc_life(nxc_u[1]) + " (unchanged)")
endfunction

function nxc_p1 takes nothing returns nothing
    call nxc_make(0, 0, 'ogru', 0, 0)
    call nxc_make(1, 1, 'uloc', 100, 0)
endfunction

// ---------------------------------------------------------------- phase 2: enemy-target spells
function nxc_p2check takes nothing returns nothing
    call NxLog("NXTEST frost nova: target life " + nxc_life(nxc_u[11]) + " (from 420: -150) Bfro " + nxc_lvl(nxc_u[11], 'Bfro') + ", neighbour " + nxc_life(nxc_u[12]) + " (-50) Bfro " + nxc_lvl(nxc_u[12], 'Bfro'))
    call NxLog("NXTEST entangling roots: BEer " + nxc_lvl(nxc_u[13], 'BEer') + " life " + nxc_life(nxc_u[13]) + " (from 420, 15/s)")
    call NxLog("NXTEST forked lightning: lives " + nxc_life(nxc_u[14]) + " " + nxc_life(nxc_u[15]) + " " + nxc_life(nxc_u[16]) + " (from 420: -85 each)")
    call NxLog("NXTEST siphon mana: caster mana " + nxc_mana(nxc_u[4]) + " (from 0 after paying), target " + nxc_mana(nxc_u[17]) + " (from 200)")
    call NxLog("NXTEST howl of terror: BNht " + nxc_lvl(nxc_u[18], 'BNht'))
endfunction

function nxc_p2cast takes nothing returns nothing
    // just the 10 mana Siphon Mana costs: what the caster has afterwards was drained
    call SetUnitState(nxc_u[4], UNIT_STATE_MANA, 10)
    call NxLog("NXTEST p2 orders: frostnova " + nxc_b2s(IssueTargetOrder(nxc_u[0], "frostnova", nxc_u[11])) + " roots " + nxc_b2s(IssueTargetOrder(nxc_u[1], "entanglingroots", nxc_u[13])) + " forked " + nxc_b2s(IssueTargetOrder(nxc_u[2], "forkedlightning", nxc_u[14])) + " siphon " + nxc_b2s(IssueTargetOrder(nxc_u[4], "drain", nxc_u[17])) + " howl " + nxc_b2s(IssueImmediateOrder(nxc_u[3], "howlofterror")))
endfunction

function nxc_p2 takes nothing returns nothing
    local integer i = 11
    call nxc_caster(0, 0, 'AUfn', -300, -200)
    call nxc_caster(1, 0, 'AEer', -300, 0)
    call nxc_caster(2, 0, 'ANfl', -300, 200)
    call nxc_caster(3, 0, 'ANht', 150, 350)
    call nxc_caster(4, 0, 'AHdr', -300, 400)
    call SetUnitState(nxc_u[4], UNIT_STATE_MANA, 0)
    loop
        exitwhen i > 18
        call nxc_make(i, 1, 'hfoo', 50, -300 + (i - 11) * 100)
        call SetUnitState(nxc_u[i], UNIT_STATE_MAX_LIFE, 420)
        call SetWidgetLife(nxc_u[i], 420)
        set i = i + 1
    endloop
    // forked lightning: three in a row behind the first
    call SetUnitPosition(nxc_u[15], nxc_x + 150, nxc_y - 60)
    call SetUnitPosition(nxc_u[16], nxc_x + 250, nxc_y - 110)
    // the siphon target is a priest with mana
    call RemoveUnit(nxc_u[17])
    call nxc_make(17, 1, 'hmpr', 0, 400)
    call SetUnitState(nxc_u[17], UNIT_STATE_MANA, 200)
endfunction

// ---------------------------------------------------------------- phase 3: buffs moved and removed
function nxc_p3check2 takes nothing returns nothing
    call NxLog("NXTEST spell steal: enemy Bblo " + nxc_lvl(nxc_u[10], 'Bblo') + " (expect 0), own footman Bblo " + nxc_lvl(nxc_u[12], 'Bblo') + " (expect 1)")
    call NxLog("NXTEST devour magic: enemy Bblo " + nxc_lvl(nxc_u[11], 'Bblo') + " (expect 0), caster mana " + nxc_mana(nxc_u[2]))
    call NxLog("NXTEST anti-magic shield: Bams " + nxc_lvl(nxc_u[14], 'Bams') + " frost armor BUfa " + nxc_lvl(nxc_u[13], 'BUfa'))
endfunction

function nxc_p3steal takes nothing returns nothing
    call NxLog("NXTEST p3 bloodlust: enemies Bblo " + nxc_lvl(nxc_u[10], 'Bblo') + " " + nxc_lvl(nxc_u[11], 'Bblo'))
    call SetUnitState(nxc_u[2], UNIT_STATE_MANA, 100)
    call NxLog("NXTEST p3 orders: spellsteal " + nxc_b2s(IssueTargetOrder(nxc_u[1], "spellsteal", nxc_u[10])) + " devourmagic " + nxc_b2s(IssuePointOrder(nxc_u[2], "devourmagic", GetUnitX(nxc_u[11]), GetUnitY(nxc_u[11]))) + " antimagic " + nxc_b2s(IssueTargetOrder(nxc_u[3], "antimagicshell", nxc_u[14])) + " frostarmor " + nxc_b2s(IssueTargetOrder(nxc_u[4], "frostarmor", nxc_u[13])))
    call TimerStart(CreateTimer(), 2.0, false, function nxc_p3check2)
endfunction

function nxc_p3cast takes nothing returns nothing
    call IssueTargetOrder(nxc_u[0], "bloodlust", nxc_u[10])
endfunction

function nxc_p3lust2 takes nothing returns nothing
    call IssueTargetOrder(nxc_u[5], "bloodlust", nxc_u[11])
endfunction

function nxc_p3 takes nothing returns nothing
    call nxc_caster(0, 1, 'Ablo', 300, 0)
    call nxc_caster(5, 1, 'Ablo', 300, 200)
    call nxc_caster(1, 0, 'Asps', -200, 0)
    call nxc_caster(2, 0, 'Advm', -200, 200)
    call nxc_caster(3, 0, 'Aams', -200, -200)
    call nxc_caster(4, 0, 'AUfu', -200, -400)
    call nxc_make(10, 1, 'hfoo', 150, 0)
    call nxc_make(11, 1, 'hfoo', 150, 250)
    call nxc_make(12, 0, 'ogru', -50, -100)
    call nxc_make(13, 0, 'ogru', -50, -350)
    call nxc_make(14, 0, 'ogru', -50, -550)
endfunction

// ---------------------------------------------------------------- phase 4: on death
function nxc_p4hatch takes nothing returns nothing
    call NxLog("NXTEST phoenix 8 s later: eggs " + I2S(nxc_count(Player(1), 'hpxe')) + " (expect 0) phoenixes " + I2S(nxc_count(Player(1), 'hphx')) + " (expect 1)")
    call nxc_clearType('hphx')
    call nxc_clearType('nspr')
endfunction

function nxc_dist takes unit a, unit b returns string
    return I2S(R2I(SquareRoot((GetUnitX(a) - GetUnitX(b)) * (GetUnitX(a) - GetUnitX(b)) + (GetUnitY(a) - GetUnitY(b)) * (GetUnitY(a) - GetUnitY(b)))))
endfunction

function nxc_p4sapper takes nothing returns nothing
    call NxLog("NXTEST sapper death damage: grunt lives " + nxc_life(nxc_u[2]) + " " + nxc_life(nxc_u[3]) + " (right after the blast: full 500 within 100, partial 200 within 250, less armor)")
endfunction

function nxc_p4check takes nothing returns nothing
    call NxLog("NXTEST spawn on death: player 1 spiders " + I2S(nxc_count(Player(1), 'nspr')) + " (expect 2)")
    call NxLog("NXTEST phoenix: eggs " + I2S(nxc_count(Player(1), 'hpxe')) + " (expect 1)")
    call TimerStart(CreateTimer(), 8.0, false, function nxc_p4hatch)
endfunction

function nxc_p4kill takes nothing returns nothing
    call NxLog("NXTEST sapper: grunts " + nxc_dist(nxc_u[1], nxc_u[2]) + " and " + nxc_dist(nxc_u[1], nxc_u[3]) + " away, lives before " + nxc_life(nxc_u[2]) + " " + nxc_life(nxc_u[3]))
    call KillUnit(nxc_u[0])
    call KillUnit(nxc_u[1])
    call nxc_p4sapper()
    call KillUnit(nxc_u[4])
endfunction

function nxc_p4 takes nothing returns nothing
    call nxc_make(0, 1, 'hfoo', 900, -600)
    call UnitAddAbility(nxc_u[0], 'Aspd')
    call nxc_make(1, 1, 'hfoo', 0, 0)
    call UnitAddAbility(nxc_u[1], 'Adda')
    call nxc_make(2, 0, 'ogru', 80, 0)
    call nxc_make(3, 0, 'ogru', 0, 180)
    call SetUnitPosition(nxc_u[2], nxc_x + 70, nxc_y)
    call SetUnitPosition(nxc_u[3], nxc_x, nxc_y + 180)
    // paused: the kobold camp by the map centre would draw them off
    call PauseUnit(nxc_u[1], true)
    call PauseUnit(nxc_u[2], true)
    call PauseUnit(nxc_u[3], true)
    call nxc_make(4, 1, 'hphx', -400, -300)
endfunction

// ---------------------------------------------------------------- phase 5: area spells
function nxc_p5check takes nothing returns nothing
    call NxLog("NXTEST monsoon: lives " + nxc_life(nxc_u[10]) + " " + nxc_life(nxc_u[11]) + " (from 420, 20/s)")
    call NxLog("NXTEST rain of fire: lives " + nxc_life(nxc_u[12]) + " " + nxc_life(nxc_u[13]) + " (from 420)")
    call NxLog("NXTEST taunt: enemy orders " + OrderId2String(GetUnitCurrentOrder(nxc_u[14])) + " " + OrderId2String(GetUnitCurrentOrder(nxc_u[15])) + ", caster life " + nxc_life(nxc_u[2]) + " footmen at " + nxc_dist(nxc_u[2], nxc_u[14]) + " " + nxc_dist(nxc_u[2], nxc_u[15]))
    call NxLog("NXTEST big bad voodoo: own grunt BOvd " + nxc_lvl(nxc_u[16], 'BOvd') + " caster order " + OrderId2String(GetUnitCurrentOrder(nxc_u[3])))
    call NxLog("NXTEST tornado: player 0 tornadoes " + I2S(nxc_count(Player(0), 'ntor')) + " (expect 1)")
    call NxLog("NXTEST rain of chaos: player 0 infernals " + I2S(nxc_count(Player(0), 'ninf')) + " (expect 2)")
    call nxc_clearType('ntor')
    call nxc_clearType('ninf')
endfunction

function nxc_p5cast takes nothing returns nothing
    call NxLog("NXTEST p5 orders: monsoon " + nxc_b2s(IssuePointOrder(nxc_u[0], "monsoon", GetUnitX(nxc_u[10]), GetUnitY(nxc_u[10]))) + " rainoffire " + nxc_b2s(IssuePointOrder(nxc_u[1], "rainoffire", GetUnitX(nxc_u[12]), GetUnitY(nxc_u[12]))) + " taunt " + nxc_b2s(IssueImmediateOrder(nxc_u[2], "taunt")) + " voodoo " + nxc_b2s(IssueImmediateOrder(nxc_u[3], "voodoo")) + " tornado " + nxc_b2s(IssuePointOrder(nxc_u[4], "tornado", nxc_x + 900, nxc_y - 700)) + " rainofchaos " + nxc_b2s(IssuePointOrder(nxc_u[5], "rainofchaos", nxc_x - 700, nxc_y + 700)))
endfunction

function nxc_p5 takes nothing returns nothing
    call nxc_caster(0, 0, 'ANmo', -300, -300)
    call nxc_caster(1, 0, 'ANrf', -300, 300)
    call nxc_caster(2, 0, 'Atau', 400, 0)
    call nxc_caster(3, 0, 'AOvd', -500, 0)
    call nxc_caster(4, 0, 'ANto', 500, -400)
    call nxc_caster(5, 0, 'ANrc', -400, 500)
    call nxc_make(10, 1, 'hfoo', 0, -300)
    call nxc_make(11, 1, 'hfoo', 60, -360)
    call nxc_make(12, 1, 'hfoo', 0, 300)
    call nxc_make(13, 1, 'hfoo', 60, 360)
    call nxc_make(14, 1, 'hfoo', 550, 100)
    call nxc_make(15, 1, 'hfoo', 550, -100)
    call nxc_make(16, 0, 'ogru', -550, 100)
    call SetUnitState(nxc_u[10], UNIT_STATE_MAX_LIFE, 420)
    call SetWidgetLife(nxc_u[10], 420)
    call SetUnitState(nxc_u[11], UNIT_STATE_MAX_LIFE, 420)
    call SetWidgetLife(nxc_u[11], 420)
    call SetUnitState(nxc_u[12], UNIT_STATE_MAX_LIFE, 420)
    call SetWidgetLife(nxc_u[12], 420)
    call SetUnitState(nxc_u[13], UNIT_STATE_MAX_LIFE, 420)
    call SetWidgetLife(nxc_u[13], 420)
endfunction

// ---------------------------------------------------------------- phase 6: passives
function nxc_p6check takes nothing returns nothing
    call NxLog("NXTEST cleave: target " + nxc_life(nxc_u[10]) + " neighbours " + nxc_life(nxc_u[11]) + " " + nxc_life(nxc_u[12]) + " (from 2000; neighbours lose 40% of each hit)")
    call NxLog("NXTEST searing arrows: archer mana " + nxc_mana(nxc_u[1]) + " (from 300, 8 a shot), target " + nxc_life(nxc_u[13]))
    call NxLog("NXTEST hardened skin: with " + nxc_life(nxc_u[15]) + " without " + nxc_life(nxc_u[16]) + " (from 2000, ten 20-damage attacks)")
    call NxLog("NXTEST phase shift: Bpsh " + nxc_lvl(nxc_u[17], 'Bpsh') + " mana " + nxc_mana(nxc_u[17]) + " (from 100)")
    call NxLog("NXTEST permanent immolation: footman " + nxc_life(nxc_u[19]) + " (from 2000, 10/s) at " + nxc_dist(nxc_u[18], nxc_u[19]) + " (area 220)")
    call NxLog("NXTEST parasite: BNpa " + nxc_lvl(nxc_u[20], 'BNpa') + " life " + nxc_life(nxc_u[20]))
endfunction

function nxc_p6hits takes nothing returns nothing
    local integer i = 0
    // ten scripted attacks on each skin test unit
    loop
        exitwhen i >= 10
        call UnitDamageTarget(nxc_u[14], nxc_u[15], 20, true, false, ATTACK_TYPE_NORMAL, DAMAGE_TYPE_NORMAL, null)
        call UnitDamageTarget(nxc_u[14], nxc_u[16], 20, true, false, ATTACK_TYPE_NORMAL, DAMAGE_TYPE_NORMAL, null)
        set i = i + 1
    endloop
    call UnitDamageTarget(nxc_u[14], nxc_u[17], 10, true, false, ATTACK_TYPE_NORMAL, DAMAGE_TYPE_NORMAL, null)
    call NxLog("NXTEST phase shift right after a hit: Bpsh " + nxc_lvl(nxc_u[17], 'Bpsh') + " mana " + nxc_mana(nxc_u[17]) + " (from 100, costs 20)")
    call UnitDamageTarget(nxc_u[14], nxc_u[17], 10, true, false, ATTACK_TYPE_NORMAL, DAMAGE_TYPE_NORMAL, null)
    call NxLog("NXTEST phase shift second hit at once: mana " + nxc_mana(nxc_u[17]) + " (still 80: cooldown) life " + nxc_life(nxc_u[17]))
endfunction

function nxc_p6cast takes nothing returns nothing
    call NxLog("NXTEST p6 orders: cleave attack " + nxc_b2s(IssueTargetOrder(nxc_u[0], "attack", nxc_u[10])) + " searing attack " + nxc_b2s(IssueTargetOrder(nxc_u[1], "attack", nxc_u[13])) + " parasite " + nxc_b2s(IssueTargetOrder(nxc_u[2], "parasite", nxc_u[20])))
    call nxc_p6hits()
endfunction

function nxc_p6 takes nothing returns nothing
    local integer i = 10
    call nxc_make(0, 0, 'ogru', -100, 0)
    call UnitAddAbility(nxc_u[0], 'ANca')
    call nxc_make(1, 0, 'nskf', -100, -500)
    call UnitAddAbility(nxc_u[1], 'AHfa')
    call SetUnitState(nxc_u[1], UNIT_STATE_MAX_MANA, 300)
    call SetUnitState(nxc_u[1], UNIT_STATE_MANA, 300)
    call nxc_caster(2, 0, 'ANpa', -400, 400)
    call nxc_make(10, 1, 'hfoo', 0, 0)
    call nxc_make(11, 1, 'hfoo', 60, 60)
    call nxc_make(12, 1, 'hfoo', 60, -60)
    call nxc_make(13, 1, 'hfoo', 150, -500)
    call nxc_make(14, 0, 'ogru', 600, 600)
    call nxc_make(15, 1, 'hfoo', 700, 600)
    call UnitAddAbility(nxc_u[15], 'Assk')
    call nxc_make(16, 1, 'hfoo', 800, 600)
    call nxc_make(17, 1, 'hfoo', 700, 500)
    call UnitAddAbility(nxc_u[17], 'Apsh')
    call SetUnitState(nxc_u[17], UNIT_STATE_MAX_MANA, 100)
    call SetUnitState(nxc_u[17], UNIT_STATE_MANA, 100)
    call nxc_make(18, 0, 'ogru', -400, -250)
    call UnitAddAbility(nxc_u[18], 'ANpi')
    call nxc_make(19, 1, 'hfoo', -340, -250)
    call nxc_make(20, 1, 'hfoo', -200, 400)
    loop
        exitwhen i > 20
        call SetUnitState(nxc_u[i], UNIT_STATE_MAX_LIFE, 2000)
        call SetWidgetLife(nxc_u[i], 2000)
        set i = i + 1
    endloop
endfunction

// ---------------------------------------------------------------- phase 7: research, devour, corpses
function nxc_raised takes nothing returns integer
    local group g = CreateGroup()
    local unit u
    local integer n = 0
    call GroupEnumUnitsOfPlayer(g, Player(0), null)
    loop
        set u = FirstOfGroup(g)
        exitwhen u == null
        call GroupRemoveUnit(g, u)
        if GetUnitAbilityLevel(u, 'BUan') > 0 then
            set n = n + 1
        endif
    endloop
    call DestroyGroup(g)
    return n
endfunction

function nxc_p7check takes nothing returns nothing
    call NxLog("NXTEST flak cannon: harpy lives target " + nxc_life(nxc_u[10]) + " neighbour " + nxc_life(nxc_u[11]) + " (from 2000; the neighbour only takes flak splash)")
    call NxLog("NXTEST gyrocopter bombs: ground footman " + nxc_life(nxc_u[12]) + " (from 2000)")
    call NxLog("NXTEST barrage: harpies near the siege engine " + nxc_life(nxc_u[13]) + " " + nxc_life(nxc_u[14]) + " (from 2000)")
    call NxLog("NXTEST frag shards: riflemen target " + nxc_life(nxc_u[15]) + " outer " + nxc_life(nxc_u[16]) + " (from 2000; outer at 240: frag only)")
    call NxLog("NXTEST creep devour: footman hidden " + nxc_b2s(IsUnitHidden(nxc_u[17])) + " life " + nxc_life(nxc_u[17]))
    call NxLog("NXTEST creep animate dead: player 0 units with BUan " + I2S(nxc_raised()) + " (6 raised of any type, less any the nearby creeps killed since)")
    call NxLog("NXTEST black arrow: player 0 dark minions " + I2S(nxc_count(Player(0), 'ndr1')) + " (expect 1 per kill made within 2 s of a black arrow)")
    call nxc_clearType('hfoo')
    call nxc_clearType('ndr1')
endfunction

function nxc_p7cast takes nothing returns nothing
    local integer i = 0
    call NxLog("NXTEST gyro ground attack before research: " + nxc_b2s(IssueTargetOrder(nxc_u[1], "attack", nxc_u[12])) + " (expect false)")
    call SetPlayerTechResearched(Player(0), 'Rhgb', 1)
    call SetPlayerTechResearched(Player(0), 'Rhfc', 1)
    call SetPlayerTechResearched(Player(0), 'Rhrt', 1)
    call SetPlayerTechResearched(Player(0), 'Rhfs', 1)
    call NxLog("NXTEST p7 orders: flak " + nxc_b2s(IssueTargetOrder(nxc_u[0], "attack", nxc_u[10])) + " mortar " + nxc_b2s(IssueTargetOrder(nxc_u[3], "attack", nxc_u[15])) + " devour " + nxc_b2s(IssueTargetOrder(nxc_u[4], "creepdevour", nxc_u[17])) + " black arrow " + nxc_b2s(IssueTargetOrder(nxc_u[6], "attack", nxc_u[18])))
    // corpses for creep Animate Dead
    loop
        exitwhen i >= 3
        call KillUnit(CreateUnit(Player(PLAYER_NEUTRAL_AGGRESSIVE), 'hfoo', nxc_x - 500 + i * 60, nxc_y + 500, 0))
        set i = i + 1
    endloop
endfunction

function nxc_p7cast2 takes nothing returns nothing
    call NxLog("NXTEST gyro ground attack after research: " + nxc_b2s(IssueTargetOrder(nxc_u[1], "attack", nxc_u[12])) + " (expect true)")
    call NxLog("NXTEST p7 animate dead order: " + nxc_b2s(IssueImmediateOrder(nxc_u[5], "animatedead")))
endfunction

function nxc_p7 takes nothing returns nothing
    local integer i = 10
    call nxc_make(0, 0, 'hgyr', -200, -200)
    call nxc_make(1, 0, 'hgyr', -200, 0)
    call nxc_make(2, 0, 'hmtt', 300, 400)
    call nxc_make(3, 0, 'hmtm', -700, -500)
    call nxc_make(4, 0, 'ogru', -200, 200)
    call UnitAddAbility(nxc_u[4], 'ACdv')
    call nxc_caster(5, 0, 'ACad', -500, 400)
    call nxc_make(6, 0, 'nskf', 500, -300)
    call UnitAddAbility(nxc_u[6], 'ANba')
    call SetUnitState(nxc_u[6], UNIT_STATE_MAX_MANA, 300)
    call SetUnitState(nxc_u[6], UNIT_STATE_MANA, 300)
    call nxc_make(10, 1, 'nhar', 50, -200)
    call nxc_make(11, 1, 'nhar', 50, -110)
    call nxc_make(12, 1, 'hfoo', 50, 0)
    call nxc_make(13, 1, 'nhar', 500, 450)
    call nxc_make(14, 1, 'nhar', 550, 350)
    call nxc_make(15, 1, 'hrif', -300, -500)
    call nxc_make(16, 1, 'hrif', -300, -260)
    call nxc_make(17, 1, 'hfoo', -100, 200)
    call nxc_make(18, 1, 'hfoo', 650, -300)
    loop
        exitwhen i > 16
        call SetUnitState(nxc_u[i], UNIT_STATE_MAX_LIFE, 2000)
        call SetWidgetLife(nxc_u[i], 2000)
        set i = i + 1
    endloop
    call SetWidgetLife(nxc_u[18], 30)
endfunction

// ---------------------------------------------------------------- driver
function nxc_step takes nothing returns nothing
    set nxc_phase = nxc_phase + 1
    if nxc_phase == 1 then
        call nxc_p1()
    elseif nxc_phase == 2 then
        call nxc_p1check()
    elseif nxc_phase == 3 then
        call nxc_clear()
        call nxc_p2()
    elseif nxc_phase == 4 then
        call nxc_p2cast()
    elseif nxc_phase == 6 then
        call nxc_p2check()
        call nxc_clear()
        call nxc_p3()
    elseif nxc_phase == 7 then
        call nxc_p3cast()
        call TimerStart(CreateTimer(), 0.5, false, function nxc_p3lust2)
    elseif nxc_phase == 8 then
        call nxc_p3steal()
    elseif nxc_phase == 10 then
        call nxc_clear()
        call nxc_p4()
    elseif nxc_phase == 11 then
        call nxc_p4kill()
    elseif nxc_phase == 12 then
        call nxc_p4check()
    elseif nxc_phase == 16 then
        call nxc_clear()
        call nxc_p5()
    elseif nxc_phase == 17 then
        call nxc_p5cast()
    elseif nxc_phase == 20 then
        call nxc_p5check()
        call nxc_clear()
        call nxc_p6()
    elseif nxc_phase == 21 then
        call nxc_p6cast()
    elseif nxc_phase == 26 then
        call nxc_p6check()
        call nxc_clear()
        call nxc_p7()
    elseif nxc_phase == 27 then
        call nxc_p7cast()
    elseif nxc_phase == 28 then
        call nxc_p7cast2()
    elseif nxc_phase == 31 then
        call nxc_p7check()
        call nxc_clear()
        call NxLog("NXTEST done")
        call PauseTimer(GetExpiredTimer())
    endif
endfunction

function nxc_main takes nothing returns nothing
    // the map centre: open ground with no map units nearby (the start camera has the player's army)
    set nxc_x = 0
    set nxc_y = 0
    set nxc_g = CreateGroup()
    call NxLog("NXTEST creep spell test at " + R2S(nxc_x) + "," + R2S(nxc_y))
    call SetPlayerAlliance(Player(1), Player(0), ALLIANCE_PASSIVE, false)
    call SetPlayerAlliance(Player(0), Player(1), ALLIANCE_PASSIVE, false)
    call SetCameraPosition(nxc_x, nxc_y)
    // one step every 2 s: units made on one step are visible and act on the next
    call TimerStart(CreateTimer(), 2.0, true, function nxc_step)
endfunction
