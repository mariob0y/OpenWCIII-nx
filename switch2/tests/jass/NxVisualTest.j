// warsmash-nx PC test (NX_DEBUG step "script:NxVisualTest.j@nxv_main"): spell art checked on screenshots:
// Locust Swarm's locust units and Mirror Image's blue illusion tint. Results are lines starting "NXTEST".
native NxLog takes string s returns nothing

globals
    unit nxv_lord = null
    unit nxv_blade = null
    real nxv_x = 0
    real nxv_y = 0
endglobals

function nxv_count takes nothing returns nothing
    local group g = CreateGroup()
    local unit u
    local integer locusts = 0
    local integer blades = 0
    call GroupEnumUnitsOfPlayer(g, Player(0), null)
    loop
        set u = FirstOfGroup(g)
        exitwhen u == null
        call GroupRemoveUnit(g, u)
        if GetUnitTypeId(u) == 'uloc' then
            set locusts = locusts + 1
        elseif GetUnitTypeId(u) == 'Obla' then
            set blades = blades + 1
            // for the screenshot: the hero left of the camera centre, the image right of it
            if u != nxv_blade then
                call SetUnitPosition(u, nxv_x + 180, nxv_y + 250)
            else
                call SetUnitPosition(u, nxv_x + 20, nxv_y + 250)
            endif
            call SetUnitFacing(u, 270)
        endif
    endloop
    call DestroyGroup(g)
    call NxLog("NXTEST locust units " + I2S(locusts) + " (expect up to 20) blademasters " + I2S(blades) + " (expect 2: hero + 1 image at level 1) crypt lord life " + I2S(R2I(GetWidgetLife(nxv_lord))) + " (from 300: bites return life)")
endfunction

function nxv_cast takes nothing returns nothing
    local string a = "false"
    local string b = "false"
    call SetUnitState(nxv_lord, UNIT_STATE_MANA, 500)
    call SetUnitState(nxv_blade, UNIT_STATE_MANA, 500)
    if IssueImmediateOrder(nxv_lord, "locustswarm") then
        set a = "true"
    endif
    if IssueImmediateOrder(nxv_blade, "mirrorimage") then
        set b = "true"
    endif
    call NxLog("NXTEST orders: locustswarm " + a + " mirrorimage " + b)
endfunction

function nxv_main takes nothing returns nothing
    local integer i = 0
    set nxv_lord = CreateUnit(Player(0), 'Ucrl', nxv_x, nxv_y, 270)
    call UnitAddAbility(nxv_lord, 'AUls')
    call SetUnitAcquireRange(nxv_lord, 0)
    call SetWidgetLife(nxv_lord, 300)
    set nxv_blade = CreateUnit(Player(0), 'Obla', nxv_x + 350, nxv_y + 200, 270)
    call UnitAddAbility(nxv_blade, 'AOmi')
    call SetUnitAcquireRange(nxv_blade, 0)
    loop
        exitwhen i >= 4
        call SetUnitAcquireRange(CreateUnit(Player(1), 'hfoo', nxv_x - 300 + i * 150, nxv_y - 300, 90), 0)
        set i = i + 1
    endloop
    call SetTimeOfDay(12)
    call SetCameraField(CAMERA_FIELD_TARGET_DISTANCE, 1100, 0)
    call SetCameraPosition(nxv_x + 100, nxv_y + 250)
    call TimerStart(CreateTimer(), 2.0, false, function nxv_cast)
    call TimerStart(CreateTimer(), 6.0, false, function nxv_count)
endfunction
