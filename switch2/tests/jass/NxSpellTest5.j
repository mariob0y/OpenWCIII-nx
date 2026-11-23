// warsmash-nx PC test (NX_DEBUG step "script:NxSpellTest5.j@nx5_main"): Mirror Image (AOmi, NxSpells2).
// Results: "NXTEST" lines on stderr.
native NxLog takes string s returns nothing

globals
    unit nx5_sam = null
    group nx5_g = null
    unit nx5_image = null
    real nx5_x = 0
    real nx5_y = 0
endglobals

function nx5_isSam takes nothing returns boolean
    return GetUnitTypeId(GetFilterUnit()) == 'Osam'
endfunction

function nx5_count takes nothing returns integer
    local integer n = 0
    local unit u
    call GroupClear(nx5_g)
    call GroupEnumUnitsOfPlayer(nx5_g, Player(0), Condition(function nx5_isSam))
    set nx5_image = null
    loop
        set u = FirstOfGroup(nx5_g)
        exitwhen u == null
        call GroupRemoveUnit(nx5_g, u)
        if not IsUnitType(u, UNIT_TYPE_DEAD) then
            set n = n + 1
            if u != nx5_sam then
                set nx5_image = u
            endif
        endif
    endloop
    return n
endfunction

function nx5_end takes nothing returns nothing
    call NxLog("NXTEST samuros after killing one image: " + I2S(nx5_count()) + ", food used " + I2S(GetPlayerState(Player(0), PLAYER_STATE_RESOURCE_FOOD_USED)))
    call NxLog("NXTEST done")
endfunction

function nx5_check takes nothing returns nothing
    local integer n = nx5_count()
    call NxLog("NXTEST samuros after the cast: " + I2S(n) + " (hero + images), food used " + I2S(GetPlayerState(Player(0), PLAYER_STATE_RESOURCE_FOOD_USED)) + ", hero mana " + I2S(R2I(GetUnitState(nx5_sam, UNIT_STATE_MANA))))
    if nx5_image != null then
        call NxLog("NXTEST image level " + I2S(GetHeroLevel(nx5_image)) + " life " + I2S(R2I(GetWidgetLife(nx5_image))) + " mana " + I2S(R2I(GetUnitState(nx5_image, UNIT_STATE_MANA))) + " buff " + I2S(GetUnitAbilityLevel(nx5_image, 'BOmi')))
        call KillUnit(nx5_image)
    endif
    call TimerStart(CreateTimer(), 2.0, false, function nx5_end)
endfunction

function nx5_cast takes nothing returns nothing
    call NxLog("NXTEST order mirrorimage: " + I2S(GetUnitAbilityLevel(nx5_sam, 'AOmi')) + " " + I2S(nx5_count()))
    call IssueImmediateOrder(nx5_sam, "mirrorimage")
    call TimerStart(CreateTimer(), 2.0, false, function nx5_check)
endfunction

function nx5_main takes nothing returns nothing
    local location l = GetCameraTargetPositionLoc()
    set nx5_x = GetLocationX(l)
    set nx5_y = GetLocationY(l)
    set nx5_g = CreateGroup()
    call NxLog("NXTEST spell test 5 at " + R2S(nx5_x) + "," + R2S(nx5_y) + ", food used " + I2S(GetPlayerState(Player(0), PLAYER_STATE_RESOURCE_FOOD_USED)))
    set nx5_sam = CreateUnit(Player(0), 'Osam', nx5_x, nx5_y, 0)
    call SetHeroLevel(nx5_sam, 10, false)
    call SelectHeroSkill(nx5_sam, 'AOmi')
    call SelectHeroSkill(nx5_sam, 'AOmi')
    call SetUnitState(nx5_sam, UNIT_STATE_MANA, 1000)
    call SetCameraPosition(nx5_x, nx5_y)
    call TimerStart(CreateTimer(), 1.5, false, function nx5_cast)
endfunction
