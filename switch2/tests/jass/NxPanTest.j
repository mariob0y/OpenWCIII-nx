// warsmash-nx PC test (NX_DEBUG step "script:NxPanTest.j@nxp_main"): moves the camera over a 3x3 grid of the
// map's camera bounds, one spot every 2 s, for "shot" steps timed to match (terrain rendering checks).
native NxLog takes string s returns nothing

globals
    integer nxp_i = 0
endglobals

function nxp_next takes nothing returns nothing
    local real minX = GetCameraBoundMinX()
    local real minY = GetCameraBoundMinY()
    local real w = GetCameraBoundMaxX() - minX
    local real h = GetCameraBoundMaxY() - minY
    local real x = minX + w * (0.15 + 0.35 * ModuloInteger(nxp_i, 3))
    local real y = minY + h * (0.15 + 0.35 * (nxp_i / 3))
    call SetCameraPosition(x, y)
    call NxLog("NXTEST camera spot " + I2S(nxp_i) + " at " + R2S(x) + "," + R2S(y))
    set nxp_i = nxp_i + 1
    if nxp_i >= 9 then
        call PauseTimer(GetExpiredTimer())
    endif
endfunction

function nxp_main takes nothing returns nothing
    call TimerStart(CreateTimer(), 2.0, true, function nxp_next)
    call nxp_next()
endfunction
