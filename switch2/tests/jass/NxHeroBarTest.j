// warsmash-nx PC test of the hero bar (NX_DEBUG "OrcX01@5:key:ESCAPE;OrcX01@8:script:NxHeroBarTest.j@nxh_main",
// then clicks and shots, and "script:NxHeroBarTest.j@nxh_check" to log the selection): gives Player 0 a
// second hero (a Far Seer) and a third that dies (a Blademaster), and Rexxar an unspent skill point, so the
// bar shows three buttons, the first one glowing and the third one greyed.
native NxLog takes string s returns nothing

globals
    unit nxh_seer = null
    unit nxh_blade = null
endglobals

function nxh_b takes boolean b returns string
    if b then
        return "true"
    endif
    return "false"
endfunction

function nxh_main takes nothing returns nothing
    set nxh_seer = CreateUnit(Player(0), 'Ofar', GetUnitX(udg_Rexxar) + 200, GetUnitY(udg_Rexxar), 270)
    set nxh_blade = CreateUnit(Player(0), 'Obla', GetUnitX(udg_Rexxar) - 200, GetUnitY(udg_Rexxar), 270)
    call KillUnit(nxh_blade)
    call UnitModifySkillPoints(udg_Rexxar, 1)
    call NxLog("NXTEST hero bar setup: rexxar skill points " + I2S(GetHeroSkillPoints(udg_Rexxar)))
endfunction

function nxh_isSeer takes nothing returns boolean
    return GetUnitTypeId(GetFilterUnit()) == 'Ofar'
endfunction

// globals start over at each script load, so the Far Seer is looked up again
function nxh_check takes nothing returns nothing
    local group g = CreateGroup()
    call GroupEnumUnitsOfPlayer(g, Player(0), Condition(function nxh_isSeer))
    set nxh_seer = FirstOfGroup(g)
    call NxLog("NXTEST selected rexxar " + nxh_b(IsUnitSelected(udg_Rexxar, Player(0))) + " seer " + nxh_b(IsUnitSelected(nxh_seer, Player(0))) + " camera " + I2S(R2I(GetCameraTargetPositionX())) + "," + I2S(R2I(GetCameraTargetPositionY())) + " seer at " + I2S(R2I(GetUnitX(nxh_seer))) + "," + I2S(R2I(GetUnitY(nxh_seer))))
endfunction

function nxh_isHero takes nothing returns boolean
    return IsUnitType(GetFilterUnit(), UNIT_TYPE_HERO)
endfunction

function nxh_heroes takes nothing returns nothing
    local group g = CreateGroup()
    local unit u
    call GroupEnumUnitsOfPlayer(g, Player(0), Condition(function nxh_isHero))
    loop
        set u = FirstOfGroup(g)
        exitwhen u == null
        call GroupRemoveUnit(g, u)
        call NxLog("NXTEST hero " + UnitId2String(GetUnitTypeId(u)) + " " + GetHeroProperName(u) + " alive " + nxh_b(GetUnitState(u, UNIT_STATE_LIFE) > 0.405) + " hidden " + nxh_b(IsUnitHidden(u)) + " selected " + nxh_b(IsUnitSelected(u, Player(0))))
    endloop
endfunction
