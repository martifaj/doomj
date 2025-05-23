package com.doomviewer.audio;

public enum SoundKey {
    // Weapon sounds
    SFX_PISTOL("DSPISTOL"),
    SFX_SHOTGN("DSSHOTGN"),
    SFX_SGCOCK("DSSGCOCK"),
    SFX_DSHTGN("DSDSHTGN"),
    SFX_DBOPN("DSDBOPN"),
    SFX_DBCLS("DSDBCLS"),
    SFX_DBLOAD("DSDBLOAD"),
    SFX_PLASMA("DSPLASMA"),
    SFX_BFG("DSBFG"),
    SFX_SAWUP("DSSAWUP"),
    SFX_SAWIDL("DSSAWIDL"),
    SFX_SAWFUL("DSSAWFUL"),
    SFX_SAWHIT("DSSAWHIT"),
    SFX_RLAUNC("DSRLAUNC"),
    SFX_RXPLOD("DSRXPLOD"),
    SFX_FIRSHT("DSFIRSHT"),
    SFX_FIRXPL("DSFIRXPL"),
    
    // Player sounds
    SFX_PLPAIN("DSPLPAIN"),
    SFX_PLDETH("DSPLDETH"),
    SFX_PDIEHI("DSPDIEHI"),
    SFX_PODTH1("DSPODTH1"),
    SFX_PODTH2("DSPODTH2"),
    SFX_PODTH3("DSPODTH3"),
    SFX_BGDTH1("DSBGDTH1"),
    SFX_BGDTH2("DSBGDTH2"),
    
    // Monster sounds
    SFX_POSIT1("DSPOSIT1"),
    SFX_POSIT2("DSPOSIT2"),
    SFX_POSIT3("DSPOSIT3"),
    SFX_BGSIT1("DSBGSIT1"),
    SFX_BGSIT2("DSBGSIT2"),
    SFX_SGTSIT("DSSGTSIT"),
    SFX_CACSIT("DSCACSIT"),
    SFX_BRSSIT("DSBRSSIT"),
    SFX_CYBSIT("DSCYBSIT"),
    SFX_SPISIT("DSSPISIT"),
    SFX_BSPSIT("DSBSPSIT"),
    SFX_KNTSIT("DSKNTSIT"),
    SFX_VILSIT("DSVILSIT"),
    SFX_MANSIT("DSMANSIT"),
    SFX_PESIT("DSPESIT"),
    SFX_SKLATK("DSSKLATK"),
    SFX_SGTATK("DSSGTATK"),
    SFX_SKEPCH("DSSKEPCH"),
    SFX_VILATK("DSVILATK"),
    SFX_CLAW("DSCLAW"),
    SFX_SKESWG("DSSKESWG"),
    SFX_PLDETH2("DSPLDETH"),
    SFX_PDIEHI2("DSPDIEHI"),
    SFX_ITEMUP("DSITEMUP"),
    SFX_WPNUP("DSWPNUP"),
    SFX_OOF("DSOOF"),
    SFX_TELEPT("DSTELEPT"),
    
    // Imp sounds
    SFX_BGSIT("DSBGSIT1"),
    SFX_BGDTH("DSBGDTH1"),
    SFX_BGACT("DSBGACT"),
    SFX_BGPAIN("DSBGPAIN"),
    SFX_FIRSHT2("DSFIRSHT"),
    
    // Environment sounds
    SFX_BAREXP("DSBAREXP"),
    SFX_PUNCH("DSPUNCH"),
    SFX_HOOF("DSHOOF"),
    SFX_METAL("DSMETAL"),
    SFX_CHGUN("DSCHGUN"),
    SFX_TINK("DSTINK"),
    SFX_BDOPN("DSBDOPN"),
    SFX_BDCLS("DSBDCLS"),
    SFX_ITMBK("DSITMBK"),
    SFX_FLAME("DSFLAME"),
    SFX_FLAMST("DSFLAMST"),
    SFX_GETPOW("DSGETPOW"),
    SFX_BOSPIT("DSBOSPIT"),
    SFX_BOSCUB("DSBOSCUB"),
    SFX_BOSSIT("DSBOSSIT"),
    SFX_BOSPN("DSBOSPN"),
    SFX_BOSDTH("DSBOSDTH"),
    SFX_MANATK("DSMANATK"),
    SFX_MANDTH("DSMANDTH"),
    SFX_SSSIT("DSSSSIT"),
    SFX_SSDTH("DSSSDTH"),
    SFX_KEENPN("DSKEENPN"),
    SFX_KEENDT("DSKEENDT"),
    SFX_SKEACT("DSSKEACT"),
    SFX_SKESIT("DSSKESIT"),
    SFX_SKEATK("DSSKEATK"),
    SFX_RADIO("DSRADIO"),
    
    // Null sound
    SFX_NONE("");
    
    private final String lumpName;
    
    SoundKey(String lumpName) {
        this.lumpName = lumpName;
    }
    
    public String getLumpName() {
        return lumpName;
    }
    
    public static SoundKey fromLumpName(String lumpName) {
        for (SoundKey key : values()) {
            if (key.lumpName.equalsIgnoreCase(lumpName)) {
                return key;
            }
        }
        return SFX_NONE;
    }
}