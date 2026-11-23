// warsmash-nx PC test (NX_DEBUG step "script:NxReviveTest.j@nxr_main"): kills Rexxar, so the map's
// resurrection stone revives him; the play bot must still be able to order him afterwards.
native NxLog takes string s returns nothing

function nxr_main takes nothing returns nothing
    call NxLog("NXTEST killing Rexxar at " + I2S(R2I(GetUnitX(udg_Rexxar))) + "," + I2S(R2I(GetUnitY(udg_Rexxar))))
    call KillUnit(udg_Rexxar)
endfunction
