// warsmash-nx PC test (NX_DEBUG step "script:NxPathTest.j@nxpt_main"): a long detour. On OrcX01 a ground unit
// east of Bloodfeather's plateau (3055,5274) is ordered to the harpy's spawn (~2400,5770): the plateau is
// reached only by a long road round the cliffs, past the A* iteration cap. Logs the unit's way every 5 s.
native NxLog takes string s returns nothing

globals
    unit nxpt_u = null
    real nxpt_gx = 2400
    real nxpt_gy = 5770
    integer nxpt_n = 0
    real nxpt_lx = 0
    real nxpt_ly = 0
    real nxpt_walked = 0
endglobals

function nxpt_tick takes nothing returns nothing
    local real x = GetUnitX(nxpt_u)
    local real y = GetUnitY(nxpt_u)
    local real d = SquareRoot((x - nxpt_gx) * (x - nxpt_gx) + (y - nxpt_gy) * (y - nxpt_gy))
    set nxpt_walked = nxpt_walked + SquareRoot((x - nxpt_lx) * (x - nxpt_lx) + (y - nxpt_ly) * (y - nxpt_ly))
    set nxpt_lx = x
    set nxpt_ly = y
    set nxpt_n = nxpt_n + 1
    call NxLog("NXTEST t+" + I2S(nxpt_n * 5) + " at " + I2S(R2I(x)) + "," + I2S(R2I(y)) + " to goal " + I2S(R2I(d)) + " walked " + I2S(R2I(nxpt_walked)) + " order " + OrderId2String(GetUnitCurrentOrder(nxpt_u)))
    if d < 300 or nxpt_n >= 36 then
        call NxLog("NXTEST done: reached " + I2S(R2I(d)) + " from the goal after walking " + I2S(R2I(nxpt_walked)))
        call PauseTimer(GetExpiredTimer())
    endif
endfunction

function nxpt_main takes nothing returns nothing
    set nxpt_u = CreateUnit(Player(0), 'ogru', 3055, 5274, 0)
    call SetUnitInvulnerable(nxpt_u, true)
    call SetUnitAcquireRange(nxpt_u, 0)
    call SetUnitMoveSpeed(nxpt_u, 522)
    set nxpt_lx = GetUnitX(nxpt_u)
    set nxpt_ly = GetUnitY(nxpt_u)
    call NxLog("NXTEST path test: start " + I2S(R2I(nxpt_lx)) + "," + I2S(R2I(nxpt_ly)) + " order " + I2S(0))
    if IssuePointOrder(nxpt_u, "move", nxpt_gx, nxpt_gy) then
        call NxLog("NXTEST move order true")
    endif
    call TimerStart(CreateTimer(), 5.0, true, function nxpt_tick)
endfunction
