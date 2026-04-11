package dev.mcrib884.musync.client

import dev.mcrib884.musync.network.MusicClientInfoPacket
import dev.mcrib884.musync.network.PacketIO
import dev.mcrib884.musync.network.PacketHandler
import dev.mcrib884.musync.network.TrackManifestEntry
import dev.mcrib884.musync.network.TrackRequestPacket
import net.minecraft.client.Minecraft
import java.io.File
import java.security.MessageDigest
import java.util.Locale

data class MuSyncThemePalette(
    val frameColor: Int,
    val panelColor: Int,
    val accentColor: Int,
    val accentStrongColor: Int,
    val buttonColor: Int,
    val buttonHoverColor: Int,
    val buttonDisabledColor: Int,
    val buttonDisabledBorderColor: Int,
    val buttonTextColor: Int,
    val buttonDisabledTextColor: Int,
    val listEvenColor: Int,
    val listOddColor: Int,
    val listHoverColor: Int,
    val listSelectedColor: Int,
    val listBorderColor: Int,
    val statusPlayingColor: Int,
    val statusWaitingColor: Int,
    val statusLoadingColor: Int,
    val statusIdleColor: Int,
    val progressBackgroundColor: Int,
    val progressFillColor: Int,
    val progressFillHighlightColor: Int,
    val progressBorderColor: Int
)

enum class MuSyncThemePreset(val id: String, val label: String, val palette: MuSyncThemePalette) {
    NEO_GREEN(
        "neo_green",
        "Neo Green",
        MuSyncThemePalette(
            frameColor = 0xFF1A1A2E.toInt(),
            panelColor = 0xE0101020.toInt(),
            accentColor = 0xFF00CC66.toInt(),
            accentStrongColor = 0xFF00EE88.toInt(),
            buttonColor = 0xFF1C1C2A.toInt(),
            buttonHoverColor = 0xFF334433.toInt(),
            buttonDisabledColor = 0xFF111118.toInt(),
            buttonDisabledBorderColor = 0xFF336644.toInt(),
            buttonTextColor = 0xFFFFFFFF.toInt(),
            buttonDisabledTextColor = 0xFF667766.toInt(),
            listEvenColor = 0x442A2C3E.toInt(),
            listOddColor = 0x33202233.toInt(),
            listHoverColor = 0x6634364A.toInt(),
            listSelectedColor = 0x661F3A31.toInt(),
            listBorderColor = 0x884A4D68.toInt(),
            statusPlayingColor = 0xFF55FF55.toInt(),
            statusWaitingColor = 0xFFFFAA00.toInt(),
            statusLoadingColor = 0xFF66CCFF.toInt(),
            statusIdleColor = 0xFFAAAAAA.toInt(),
            progressBackgroundColor = 0xFF222244.toInt(),
            progressFillColor = 0xFF00CC66.toInt(),
            progressFillHighlightColor = 0xFF00EE88.toInt(),
            progressBorderColor = 0xFF333355.toInt()
        )
    ),
    OCEAN_TEAL(
        "ocean_teal",
        "Ocean Teal",
        MuSyncThemePalette(
            frameColor = 0xFF10222A.toInt(),
            panelColor = 0xE00D1C24.toInt(),
            accentColor = 0xFF2DB6A3.toInt(),
            accentStrongColor = 0xFF53D9C7.toInt(),
            buttonColor = 0xFF16262F.toInt(),
            buttonHoverColor = 0xFF23414D.toInt(),
            buttonDisabledColor = 0xFF101820.toInt(),
            buttonDisabledBorderColor = 0xFF2D5F63.toInt(),
            buttonTextColor = 0xFFE9FFFF.toInt(),
            buttonDisabledTextColor = 0xFF7AA0A3.toInt(),
            listEvenColor = 0x44303F4A.toInt(),
            listOddColor = 0x33212A33.toInt(),
            listHoverColor = 0x6641545E.toInt(),
            listSelectedColor = 0x66284C47.toInt(),
            listBorderColor = 0x88667E89.toInt(),
            statusPlayingColor = 0xFF7CF7D3.toInt(),
            statusWaitingColor = 0xFFFFC96B.toInt(),
            statusLoadingColor = 0xFF7CC5FF.toInt(),
            statusIdleColor = 0xFFB5C4CC.toInt(),
            progressBackgroundColor = 0xFF1F3038.toInt(),
            progressFillColor = 0xFF2DB6A3.toInt(),
            progressFillHighlightColor = 0xFF53D9C7.toInt(),
            progressBorderColor = 0xFF325161.toInt()
        )
    ),
    SUNSET_AMBER(
        "sunset_amber",
        "Sunset Amber",
        MuSyncThemePalette(
            frameColor = 0xFF2A1B12.toInt(),
            panelColor = 0xE0241712.toInt(),
            accentColor = 0xFFE08C2E.toInt(),
            accentStrongColor = 0xFFFFB04D.toInt(),
            buttonColor = 0xFF2D1E18.toInt(),
            buttonHoverColor = 0xFF473327.toInt(),
            buttonDisabledColor = 0xFF1C1411.toInt(),
            buttonDisabledBorderColor = 0xFF6A4A30.toInt(),
            buttonTextColor = 0xFFFFF3E6.toInt(),
            buttonDisabledTextColor = 0xFFA88C72.toInt(),
            listEvenColor = 0x4443382F.toInt(),
            listOddColor = 0x33322A23.toInt(),
            listHoverColor = 0x66574638.toInt(),
            listSelectedColor = 0x66543B24.toInt(),
            listBorderColor = 0x889C7D62.toInt(),
            statusPlayingColor = 0xFFFFCB80.toInt(),
            statusWaitingColor = 0xFFFFE180.toInt(),
            statusLoadingColor = 0xFF9CCBFF.toInt(),
            statusIdleColor = 0xFFC9B19A.toInt(),
            progressBackgroundColor = 0xFF3A2A22.toInt(),
            progressFillColor = 0xFFE08C2E.toInt(),
            progressFillHighlightColor = 0xFFFFB04D.toInt(),
            progressBorderColor = 0xFF5A4331.toInt()
        )
    ),
    CLEAR_SLATE(
        "clear_slate",
        "Clear Slate",
        MuSyncThemePalette(
            frameColor = 0x70AFC6D9.toInt(),
            panelColor = 0x88F5FAFF.toInt(),
            accentColor = 0xFF2F6FA1.toInt(),
            accentStrongColor = 0xFF4B8BC0.toInt(),
            buttonColor = 0xA0F0F4F8.toInt(),
            buttonHoverColor = 0xB8E0EAF2.toInt(),
            buttonDisabledColor = 0xFFE1E5EA.toInt(),
            buttonDisabledBorderColor = 0xFF9DAFC0.toInt(),
            buttonTextColor = 0xFF10253A.toInt(),
            buttonDisabledTextColor = 0xFF74879A.toInt(),
            listEvenColor = 0xFFEAF0F5.toInt(),
            listOddColor = 0xFFF4F8FB.toInt(),
            listHoverColor = 0xFFD7E4EF.toInt(),
            listSelectedColor = 0xFFC9DFEF.toInt(),
            listBorderColor = 0xFFB7C7D5.toInt(),
            statusPlayingColor = 0xFF2F8A4F.toInt(),
            statusWaitingColor = 0xFFC07A1F.toInt(),
            statusLoadingColor = 0xFF2F6FA1.toInt(),
            statusIdleColor = 0xFF667788.toInt(),
            progressBackgroundColor = 0x8CDCE5EE.toInt(),
            progressFillColor = 0xFF2F6FA1.toInt(),
            progressFillHighlightColor = 0xFF4B8BC0.toInt(),
            progressBorderColor = 0xFFB6C8D8.toInt()
        )
    ),
    CLEAR_MINT(
        "clear_mint",
        "Clear Mint",
        MuSyncThemePalette(
            frameColor = 0x70A8D8C7.toInt(),
            panelColor = 0x88F3FFFA.toInt(),
            accentColor = 0xFF2F9C79.toInt(),
            accentStrongColor = 0xFF4AC39A.toInt(),
            buttonColor = 0xA0EEF8F4.toInt(),
            buttonHoverColor = 0xB8DDF1E8.toInt(),
            buttonDisabledColor = 0xFFE0ECE7.toInt(),
            buttonDisabledBorderColor = 0xFF94B7AA.toInt(),
            buttonTextColor = 0xFF11352B.toInt(),
            buttonDisabledTextColor = 0xFF6A8E80.toInt(),
            listEvenColor = 0xFFE7F3EE.toInt(),
            listOddColor = 0xFFF2FBF7.toInt(),
            listHoverColor = 0xFFD3ECE2.toInt(),
            listSelectedColor = 0xFFC1E7D8.toInt(),
            listBorderColor = 0xFFAED3C3.toInt(),
            statusPlayingColor = 0xFF2F9C79.toInt(),
            statusWaitingColor = 0xFFB7852A.toInt(),
            statusLoadingColor = 0xFF2F76A1.toInt(),
            statusIdleColor = 0xFF5D7A6E.toInt(),
            progressBackgroundColor = 0x8CD7ECE4.toInt(),
            progressFillColor = 0xFF2F9C79.toInt(),
            progressFillHighlightColor = 0xFF4AC39A.toInt(),
            progressBorderColor = 0xFFA8CDBF.toInt()
        )
    ),
    CLEAR_ROSE(
        "clear_rose",
        "Clear Rose",
        MuSyncThemePalette(
            frameColor = 0x70D6B7CB.toInt(),
            panelColor = 0x88FFF5FA.toInt(),
            accentColor = 0xFFAD4E78.toInt(),
            accentStrongColor = 0xFFD16996.toInt(),
            buttonColor = 0xA0F9EEF3.toInt(),
            buttonHoverColor = 0xB8F0DDE7.toInt(),
            buttonDisabledColor = 0xFFECE1E6.toInt(),
            buttonDisabledBorderColor = 0xFFB89AA9.toInt(),
            buttonTextColor = 0xFF3F1B2E.toInt(),
            buttonDisabledTextColor = 0xFF8E7280.toInt(),
            listEvenColor = 0xFFF3E8ED.toInt(),
            listOddColor = 0xFFFAF2F6.toInt(),
            listHoverColor = 0xFFECD9E3.toInt(),
            listSelectedColor = 0xFFE5C7D7.toInt(),
            listBorderColor = 0xFFD6B7C8.toInt(),
            statusPlayingColor = 0xFF2F8A5A.toInt(),
            statusWaitingColor = 0xFFB07A22.toInt(),
            statusLoadingColor = 0xFF7A5FAF.toInt(),
            statusIdleColor = 0xFF756875.toInt(),
            progressBackgroundColor = 0x8CECDCE4.toInt(),
            progressFillColor = 0xFFAD4E78.toInt(),
            progressFillHighlightColor = 0xFFD16996.toInt(),
            progressBorderColor = 0xFFCFB8C4.toInt()
        )
    ),
    NEO_LIME(
        "neo_lime",
        "Neo Lime",
        MuSyncThemePalette(
            frameColor = 0xFF172218.toInt(),
            panelColor = 0xE0101A11.toInt(),
            accentColor = 0xFF76FF03.toInt(),
            accentStrongColor = 0xFFB2FF59.toInt(),
            buttonColor = 0xFF1A251A.toInt(),
            buttonHoverColor = 0xFF2A3B28.toInt(),
            buttonDisabledColor = 0xFF111711.toInt(),
            buttonDisabledBorderColor = 0xFF4A6B41.toInt(),
            buttonTextColor = 0xFFF4FFE8.toInt(),
            buttonDisabledTextColor = 0xFF90A986.toInt(),
            listEvenColor = 0x44333D2A.toInt(),
            listOddColor = 0x33242E1F.toInt(),
            listHoverColor = 0x66505F3E.toInt(),
            listSelectedColor = 0x6653692F.toInt(),
            listBorderColor = 0x88768F57.toInt(),
            statusPlayingColor = 0xFFB2FF59.toInt(),
            statusWaitingColor = 0xFFFFD54F.toInt(),
            statusLoadingColor = 0xFF80D8FF.toInt(),
            statusIdleColor = 0xFFADC29F.toInt(),
            progressBackgroundColor = 0xFF263126.toInt(),
            progressFillColor = 0xFF76FF03.toInt(),
            progressFillHighlightColor = 0xFFB2FF59.toInt(),
            progressBorderColor = 0xFF49664A.toInt()
        )
    ),
    OCEAN_CYAN(
        "ocean_cyan",
        "Ocean Cyan",
        MuSyncThemePalette(
            frameColor = 0xFF0A2230.toInt(),
            panelColor = 0xE00A1A28.toInt(),
            accentColor = 0xFF00E5FF.toInt(),
            accentStrongColor = 0xFF80D8FF.toInt(),
            buttonColor = 0xFF0F2633.toInt(),
            buttonHoverColor = 0xFF1A3D4F.toInt(),
            buttonDisabledColor = 0xFF0C1822.toInt(),
            buttonDisabledBorderColor = 0xFF2F5A6F.toInt(),
            buttonTextColor = 0xFFE8FBFF.toInt(),
            buttonDisabledTextColor = 0xFF7E9EAD.toInt(),
            listEvenColor = 0x44233A47.toInt(),
            listOddColor = 0x331A2B34.toInt(),
            listHoverColor = 0x66405E6E.toInt(),
            listSelectedColor = 0x66274B5A.toInt(),
            listBorderColor = 0x88618597.toInt(),
            statusPlayingColor = 0xFF80D8FF.toInt(),
            statusWaitingColor = 0xFFFFD180.toInt(),
            statusLoadingColor = 0xFF40C4FF.toInt(),
            statusIdleColor = 0xFFA5BDCB.toInt(),
            progressBackgroundColor = 0xFF1A2E3A.toInt(),
            progressFillColor = 0xFF00E5FF.toInt(),
            progressFillHighlightColor = 0xFF80D8FF.toInt(),
            progressBorderColor = 0xFF3F5F71.toInt()
        )
    ),
    SUNSET_SCARLET(
        "sunset_scarlet",
        "Sunset Scarlet",
        MuSyncThemePalette(
            frameColor = 0xFF2A1414.toInt(),
            panelColor = 0xE0221212.toInt(),
            accentColor = 0xFFFF7043.toInt(),
            accentStrongColor = 0xFFFFAB91.toInt(),
            buttonColor = 0xFF2A1818.toInt(),
            buttonHoverColor = 0xFF4A2A26.toInt(),
            buttonDisabledColor = 0xFF1A1111.toInt(),
            buttonDisabledBorderColor = 0xFF6A4438.toInt(),
            buttonTextColor = 0xFFFFEFE8.toInt(),
            buttonDisabledTextColor = 0xFFB19386.toInt(),
            listEvenColor = 0x44453833.toInt(),
            listOddColor = 0x33322925.toInt(),
            listHoverColor = 0x66645345.toInt(),
            listSelectedColor = 0x66634837.toInt(),
            listBorderColor = 0x889C7A66.toInt(),
            statusPlayingColor = 0xFFFFAB91.toInt(),
            statusWaitingColor = 0xFFFFE082.toInt(),
            statusLoadingColor = 0xFF90CAF9.toInt(),
            statusIdleColor = 0xFFC9B2A5.toInt(),
            progressBackgroundColor = 0xFF3A2824.toInt(),
            progressFillColor = 0xFFFF7043.toInt(),
            progressFillHighlightColor = 0xFFFFAB91.toInt(),
            progressBorderColor = 0xFF64473F.toInt()
        )
    ),
    CLEAR_SKY(
        "clear_sky",
        "Clear Sky",
        MuSyncThemePalette(
            frameColor = 0x70B2D7E8.toInt(),
            panelColor = 0x88F1FAFF.toInt(),
            accentColor = 0xFF1789D4.toInt(),
            accentStrongColor = 0xFF4CB5FF.toInt(),
            buttonColor = 0xA0EBF6FF.toInt(),
            buttonHoverColor = 0xB8DDF0FF.toInt(),
            buttonDisabledColor = 0xFFDDE8EF.toInt(),
            buttonDisabledBorderColor = 0xFF97B4C8.toInt(),
            buttonTextColor = 0xFF0A2C47.toInt(),
            buttonDisabledTextColor = 0xFF6A8699.toInt(),
            listEvenColor = 0xDCE7F3FB.toInt(),
            listOddColor = 0xCCF4FAFF.toInt(),
            listHoverColor = 0xE0D2E8F8.toInt(),
            listSelectedColor = 0xE0C5DFF3.toInt(),
            listBorderColor = 0xFFAAC4D9.toInt(),
            statusPlayingColor = 0xFF1B8E5F.toInt(),
            statusWaitingColor = 0xFFB47818.toInt(),
            statusLoadingColor = 0xFF1789D4.toInt(),
            statusIdleColor = 0xFF4E6678.toInt(),
            progressBackgroundColor = 0x8CD8E8F4.toInt(),
            progressFillColor = 0xFF1789D4.toInt(),
            progressFillHighlightColor = 0xFF4CB5FF.toInt(),
            progressBorderColor = 0xFFB0C9DD.toInt()
        )
    ),
    CLEAR_CORAL(
        "clear_coral",
        "Clear Coral",
        MuSyncThemePalette(
            frameColor = 0x70E8C1B2.toInt(),
            panelColor = 0x88FFF5F1.toInt(),
            accentColor = 0xFFCC5B35.toInt(),
            accentStrongColor = 0xFFFF8A65.toInt(),
            buttonColor = 0xA0FFF0EB.toInt(),
            buttonHoverColor = 0xB8FFE1D6.toInt(),
            buttonDisabledColor = 0xFFEFE1DB.toInt(),
            buttonDisabledBorderColor = 0xFFC39E91.toInt(),
            buttonTextColor = 0xFF4A2116.toInt(),
            buttonDisabledTextColor = 0xFF8E6D63.toInt(),
            listEvenColor = 0xDCEFE4DE.toInt(),
            listOddColor = 0xCCFFF3EE.toInt(),
            listHoverColor = 0xE0F5D9CE.toInt(),
            listSelectedColor = 0xE0F0CEBF.toInt(),
            listBorderColor = 0xFFD5B5A8.toInt(),
            statusPlayingColor = 0xFF2C8A57.toInt(),
            statusWaitingColor = 0xFFAA751A.toInt(),
            statusLoadingColor = 0xFF4A7FB7.toInt(),
            statusIdleColor = 0xFF7A5F57.toInt(),
            progressBackgroundColor = 0x8CF0DDD4.toInt(),
            progressFillColor = 0xFFCC5B35.toInt(),
            progressFillHighlightColor = 0xFFFF8A65.toInt(),
            progressBorderColor = 0xFFCDAFA2.toInt()
        )
    ),
    NEO_CRIMSON(
        "neo_crimson",
        "Neo Crimson",
        MuSyncThemePalette(
            frameColor = 0xFF261316.toInt(),
            panelColor = 0xE01A1012.toInt(),
            accentColor = 0xFFFF1744.toInt(),
            accentStrongColor = 0xFFFF616F.toInt(),
            buttonColor = 0xFF28171B.toInt(),
            buttonHoverColor = 0xFF42252D.toInt(),
            buttonDisabledColor = 0xFF1A1012.toInt(),
            buttonDisabledBorderColor = 0xFF7A3E4C.toInt(),
            buttonTextColor = 0xFFFFEDF1.toInt(),
            buttonDisabledTextColor = 0xFFAF8A93.toInt(),
            listEvenColor = 0x4442363C.toInt(),
            listOddColor = 0x3333282D.toInt(),
            listHoverColor = 0x665B4550.toInt(),
            listSelectedColor = 0x666A3342.toInt(),
            listBorderColor = 0x889A5B6D.toInt(),
            statusPlayingColor = 0xFFFF616F.toInt(),
            statusWaitingColor = 0xFFFFD166.toInt(),
            statusLoadingColor = 0xFF80D8FF.toInt(),
            statusIdleColor = 0xFFC8A7B0.toInt(),
            progressBackgroundColor = 0xFF352228.toInt(),
            progressFillColor = 0xFFFF1744.toInt(),
            progressFillHighlightColor = 0xFFFF616F.toInt(),
            progressBorderColor = 0xFF68414D.toInt()
        )
    ),
    NEO_ELECTRIC(
        "neo_electric",
        "Neo Electric",
        MuSyncThemePalette(
            frameColor = 0xFF111B2D.toInt(),
            panelColor = 0xE00D1524.toInt(),
            accentColor = 0xFF00E5FF.toInt(),
            accentStrongColor = 0xFF76FFFF.toInt(),
            buttonColor = 0xFF132035.toInt(),
            buttonHoverColor = 0xFF213650.toInt(),
            buttonDisabledColor = 0xFF0E1726.toInt(),
            buttonDisabledBorderColor = 0xFF3F5F84.toInt(),
            buttonTextColor = 0xFFEAF8FF.toInt(),
            buttonDisabledTextColor = 0xFF8CA4BD.toInt(),
            listEvenColor = 0x4425364C.toInt(),
            listOddColor = 0x331B293B.toInt(),
            listHoverColor = 0x66435E7D.toInt(),
            listSelectedColor = 0x66305B73.toInt(),
            listBorderColor = 0x88708AA9.toInt(),
            statusPlayingColor = 0xFF76FFFF.toInt(),
            statusWaitingColor = 0xFFFFD180.toInt(),
            statusLoadingColor = 0xFF40C4FF.toInt(),
            statusIdleColor = 0xFFAFC0D1.toInt(),
            progressBackgroundColor = 0xFF1D2C42.toInt(),
            progressFillColor = 0xFF00E5FF.toInt(),
            progressFillHighlightColor = 0xFF76FFFF.toInt(),
            progressBorderColor = 0xFF45607F.toInt()
        )
    ),
    OCEAN_INDIGO(
        "ocean_indigo",
        "Ocean Indigo",
        MuSyncThemePalette(
            frameColor = 0xFF121A31.toInt(),
            panelColor = 0xE00F1628.toInt(),
            accentColor = 0xFF5C6BFF.toInt(),
            accentStrongColor = 0xFF8EA2FF.toInt(),
            buttonColor = 0xFF151E36.toInt(),
            buttonHoverColor = 0xFF263253.toInt(),
            buttonDisabledColor = 0xFF10162A.toInt(),
            buttonDisabledBorderColor = 0xFF47557C.toInt(),
            buttonTextColor = 0xFFEAF0FF.toInt(),
            buttonDisabledTextColor = 0xFF94A0BF.toInt(),
            listEvenColor = 0x442A3550.toInt(),
            listOddColor = 0x331F2940.toInt(),
            listHoverColor = 0x6647597D.toInt(),
            listSelectedColor = 0x66384471.toInt(),
            listBorderColor = 0x887180A8.toInt(),
            statusPlayingColor = 0xFF8EA2FF.toInt(),
            statusWaitingColor = 0xFFFFD180.toInt(),
            statusLoadingColor = 0xFF80D8FF.toInt(),
            statusIdleColor = 0xFFAAB5D2.toInt(),
            progressBackgroundColor = 0xFF222C45.toInt(),
            progressFillColor = 0xFF5C6BFF.toInt(),
            progressFillHighlightColor = 0xFF8EA2FF.toInt(),
            progressBorderColor = 0xFF4D5C85.toInt()
        )
    ),
    OCEAN_EMERALD(
        "ocean_emerald",
        "Ocean Emerald",
        MuSyncThemePalette(
            frameColor = 0xFF0F2622.toInt(),
            panelColor = 0xE00B1F1B.toInt(),
            accentColor = 0xFF00C853.toInt(),
            accentStrongColor = 0xFF69F0AE.toInt(),
            buttonColor = 0xFF123029.toInt(),
            buttonHoverColor = 0xFF1F4A3F.toInt(),
            buttonDisabledColor = 0xFF0E201B.toInt(),
            buttonDisabledBorderColor = 0xFF3C725F.toInt(),
            buttonTextColor = 0xFFE8FFF5.toInt(),
            buttonDisabledTextColor = 0xFF89B09F.toInt(),
            listEvenColor = 0x44283F36.toInt(),
            listOddColor = 0x331D3028.toInt(),
            listHoverColor = 0x66435F52.toInt(),
            listSelectedColor = 0x66385D4A.toInt(),
            listBorderColor = 0x886A957F.toInt(),
            statusPlayingColor = 0xFF69F0AE.toInt(),
            statusWaitingColor = 0xFFFFD166.toInt(),
            statusLoadingColor = 0xFF80D8FF.toInt(),
            statusIdleColor = 0xFFA4C6B8.toInt(),
            progressBackgroundColor = 0xFF1B3A31.toInt(),
            progressFillColor = 0xFF00C853.toInt(),
            progressFillHighlightColor = 0xFF69F0AE.toInt(),
            progressBorderColor = 0xFF477A66.toInt()
        )
    ),
    SUNSET_GOLD(
        "sunset_gold",
        "Sunset Gold",
        MuSyncThemePalette(
            frameColor = 0xFF2A1F0E.toInt(),
            panelColor = 0xE022180D.toInt(),
            accentColor = 0xFFFFB300.toInt(),
            accentStrongColor = 0xFFFFD54F.toInt(),
            buttonColor = 0xFF2D2212.toInt(),
            buttonHoverColor = 0xFF493626.toInt(),
            buttonDisabledColor = 0xFF1D160F.toInt(),
            buttonDisabledBorderColor = 0xFF7E6140.toInt(),
            buttonTextColor = 0xFFFFF7E6.toInt(),
            buttonDisabledTextColor = 0xFFB49F7D.toInt(),
            listEvenColor = 0x44453B2E.toInt(),
            listOddColor = 0x33342C21.toInt(),
            listHoverColor = 0x665C4D39.toInt(),
            listSelectedColor = 0x66644D2B.toInt(),
            listBorderColor = 0x889B7E58.toInt(),
            statusPlayingColor = 0xFFFFD54F.toInt(),
            statusWaitingColor = 0xFFFFE082.toInt(),
            statusLoadingColor = 0xFF90CAF9.toInt(),
            statusIdleColor = 0xFFC9B28B.toInt(),
            progressBackgroundColor = 0xFF3A2D1F.toInt(),
            progressFillColor = 0xFFFFB300.toInt(),
            progressFillHighlightColor = 0xFFFFD54F.toInt(),
            progressBorderColor = 0xFF68503A.toInt()
        )
    ),
    SUNSET_VIOLET(
        "sunset_violet",
        "Sunset Violet",
        MuSyncThemePalette(
            frameColor = 0xFF25182C.toInt(),
            panelColor = 0xE01E1424.toInt(),
            accentColor = 0xFFE040FB.toInt(),
            accentStrongColor = 0xFFEA80FC.toInt(),
            buttonColor = 0xFF291C32.toInt(),
            buttonHoverColor = 0xFF442A4F.toInt(),
            buttonDisabledColor = 0xFF1B1221.toInt(),
            buttonDisabledBorderColor = 0xFF6D4B7E.toInt(),
            buttonTextColor = 0xFFFCEBFF.toInt(),
            buttonDisabledTextColor = 0xFFB49DBF.toInt(),
            listEvenColor = 0x4440364A.toInt(),
            listOddColor = 0x33302A38.toInt(),
            listHoverColor = 0x66584A66.toInt(),
            listSelectedColor = 0x665E3E71.toInt(),
            listBorderColor = 0x889374A8.toInt(),
            statusPlayingColor = 0xFFEA80FC.toInt(),
            statusWaitingColor = 0xFFFFD180.toInt(),
            statusLoadingColor = 0xFF80D8FF.toInt(),
            statusIdleColor = 0xFFC3B2CF.toInt(),
            progressBackgroundColor = 0xFF392A44.toInt(),
            progressFillColor = 0xFFE040FB.toInt(),
            progressFillHighlightColor = 0xFFEA80FC.toInt(),
            progressBorderColor = 0xFF67517A.toInt()
        )
    ),
    CLEAR_AURORA(
        "clear_aurora",
        "Clear Aurora",
        MuSyncThemePalette(
            frameColor = 0x70B6EBD4.toInt(),
            panelColor = 0x88EEFFF7.toInt(),
            accentColor = 0xFF1DBF8A.toInt(),
            accentStrongColor = 0xFF62F4BD.toInt(),
            buttonColor = 0xA0ECFFF6.toInt(),
            buttonHoverColor = 0xB8D8FCEB.toInt(),
            buttonDisabledColor = 0xFFE0ECE7.toInt(),
            buttonDisabledBorderColor = 0xFF96BCAE.toInt(),
            buttonTextColor = 0xFF0E352B.toInt(),
            buttonDisabledTextColor = 0xFF6C8F83.toInt(),
            listEvenColor = 0xDCE4F3EC.toInt(),
            listOddColor = 0xCCF0FCF6.toInt(),
            listHoverColor = 0xE0D0F0E3.toInt(),
            listSelectedColor = 0xE0BCE9D8.toInt(),
            listBorderColor = 0xFFAFD6C8.toInt(),
            statusPlayingColor = 0xFF1F8F65.toInt(),
            statusWaitingColor = 0xFFB98524.toInt(),
            statusLoadingColor = 0xFF2D7FB2.toInt(),
            statusIdleColor = 0xFF5B7A70.toInt(),
            progressBackgroundColor = 0x8CD7ECE4.toInt(),
            progressFillColor = 0xFF1DBF8A.toInt(),
            progressFillHighlightColor = 0xFF62F4BD.toInt(),
            progressBorderColor = 0xFFADD0C3.toInt()
        )
    ),
    CLEAR_CITRUS(
        "clear_citrus",
        "Clear Citrus",
        MuSyncThemePalette(
            frameColor = 0x70E5DEB0.toInt(),
            panelColor = 0x88FFFDEB.toInt(),
            accentColor = 0xFFCC9A00.toInt(),
            accentStrongColor = 0xFFFFCA28.toInt(),
            buttonColor = 0xA0FFF9DE.toInt(),
            buttonHoverColor = 0xB8FFF1C8.toInt(),
            buttonDisabledColor = 0xFFECE7D0.toInt(),
            buttonDisabledBorderColor = 0xFFC0B58A.toInt(),
            buttonTextColor = 0xFF3D2F0E.toInt(),
            buttonDisabledTextColor = 0xFF8F8156.toInt(),
            listEvenColor = 0xDCEFE9CE.toInt(),
            listOddColor = 0xCCFFF9DE.toInt(),
            listHoverColor = 0xE0F7EDBF.toInt(),
            listSelectedColor = 0xE0F2E2AC.toInt(),
            listBorderColor = 0xFFDCCB98.toInt(),
            statusPlayingColor = 0xFF2F8B54.toInt(),
            statusWaitingColor = 0xFFB58320.toInt(),
            statusLoadingColor = 0xFF3D7CB0.toInt(),
            statusIdleColor = 0xFF776A45.toInt(),
            progressBackgroundColor = 0x8CEADEC0.toInt(),
            progressFillColor = 0xFFCC9A00.toInt(),
            progressFillHighlightColor = 0xFFFFCA28.toInt(),
            progressBorderColor = 0xFFD5C391.toInt()
        )
    ),
    CLEAR_LILAC(
        "clear_lilac",
        "Clear Lilac",
        MuSyncThemePalette(
            frameColor = 0x70DCC9EA.toInt(),
            panelColor = 0x88FAF2FF.toInt(),
            accentColor = 0xFF8E5BD6.toInt(),
            accentStrongColor = 0xFFB388FF.toInt(),
            buttonColor = 0xA0F8EDFF.toInt(),
            buttonHoverColor = 0xB8EEDCFF.toInt(),
            buttonDisabledColor = 0xFFE8E0EF.toInt(),
            buttonDisabledBorderColor = 0xFFB7A2C9.toInt(),
            buttonTextColor = 0xFF301847.toInt(),
            buttonDisabledTextColor = 0xFF7E6B96.toInt(),
            listEvenColor = 0xDCEEE2F7.toInt(),
            listOddColor = 0xCCFAF0FF.toInt(),
            listHoverColor = 0xE0E3D1F8.toInt(),
            listSelectedColor = 0xE0DAC0F1.toInt(),
            listBorderColor = 0xFFD1BBE7.toInt(),
            statusPlayingColor = 0xFF2D8A5C.toInt(),
            statusWaitingColor = 0xFFAF7D22.toInt(),
            statusLoadingColor = 0xFF4B79B3.toInt(),
            statusIdleColor = 0xFF6D5B82.toInt(),
            progressBackgroundColor = 0x8CE8DAF1.toInt(),
            progressFillColor = 0xFF8E5BD6.toInt(),
            progressFillHighlightColor = 0xFFB388FF.toInt(),
            progressBorderColor = 0xFFC9B2DE.toInt()
        )
    );

    companion object {
        private val byId = entries.associateBy { it.id }
        private val cycleOrder = entries
            .filterNot { it.id.startsWith("clear_") } +
            entries.filter { it.id.startsWith("clear_") }

        fun fromId(id: String?): MuSyncThemePreset {
            if (id.isNullOrBlank()) return NEO_GREEN
            return byId[id.lowercase(Locale.ROOT)] ?: NEO_GREEN
        }

        fun next(current: MuSyncThemePreset): MuSyncThemePreset {
            val index = cycleOrder.indexOf(current)
            if (index < 0) return NEO_GREEN
            return cycleOrder[(index + 1) % cycleOrder.size]
        }
    }
}

object ClientTrackManager {

    private val SAFE_INTERNAL_NAME = Regex("^[\\p{L}\\p{N}_\\-]+\\.(ogg|wav|mp3)$")
    private val SUPPORTED_EXTENSIONS = setOf("ogg", "wav", "mp3")

    private data class ManifestTrackState(
        val entry: TrackManifestEntry,
        var totalChunks: Int = 0,
        var chunksReceived: Int = 0,
        var bytesReceived: Long = 0L,
        var completed: Boolean = false,
        var failed: Boolean = false
    )

    private fun normalizeInternalName(name: String): String? {
        val normalized = name.lowercase(Locale.ROOT).replace(" ", "_")
        if (!SAFE_INTERNAL_NAME.matches(normalized)) {
            dev.mcrib884.musync.MuSyncLog.warn("Rejected unsafe track name: $name")
            return null
        }
        return normalized
    }

    fun displayTrackName(internalName: String): String {
        return internalName.substringBeforeLast(".")
    }

    private var serverManifest: List<TrackManifestEntry> = emptyList()
    private var serverManifestVersion: Long = -1L
    private val downloadStates = linkedMapOf<String, ManifestTrackState>()

    var tracksToDownload: List<Pair<String, Long>> = emptyList()
        private set

    var currentDownloadIndex: Int = 0
    private set

    var currentTrackChunksReceived: Int = 0
        private set
    var currentTrackTotalChunks: Int = 0
        private set

    var isDownloading: Boolean = false
        private set
    var downloadComplete: Boolean = false
        private set

    var totalBytesToDownload: Long = 0
        private set
    var totalBytesReceived: Long = 0
        private set

    private val failedTracks = linkedSetOf<String>()
    private val pendingRequested = linkedSetOf<String>()
    private var lastProgressAtMs: Long = 0L
    private var stallRetries: Int = 0
    private const val DOWNLOAD_STALL_TIMEOUT_MS = 12_000L
    private const val MAX_STALL_RETRIES = 3

    var cacheEnabled: Boolean = true
        get() {
            ensureSettingsLoaded()
            return field
        }
        private set

    private var themePreset: MuSyncThemePreset = MuSyncThemePreset.NEO_GREEN

    private var settingsLoaded = false

    fun onWorldLoaded() {
        ensureSettingsLoaded()
        if (!cacheEnabled) {
            clearCacheFolder()
        }
    }

    fun getThemePreset(): MuSyncThemePreset {
        ensureSettingsLoaded()
        return themePreset
    }

    fun setThemePreset(value: MuSyncThemePreset) {
        ensureSettingsLoaded()
        themePreset = value
        saveThemeSetting(value)
    }

    fun cycleTheme(): MuSyncThemePreset {
        val next = MuSyncThemePreset.next(getThemePreset())
        setThemePreset(next)
        return next
    }

    fun setCacheEnabled(value: Boolean) {
        ensureSettingsLoaded()
        cacheEnabled = value
        saveCacheSetting(value)
    }

    private fun ensureSettingsLoaded() {
        if (settingsLoaded) return
        settingsLoaded = true
        cacheEnabled = loadCacheSetting()
        themePreset = loadThemeSetting()
    }

    private fun getSettingsFile(): File =
        File(Minecraft.getInstance().gameDirectory, "musync_settings.properties")

    private fun loadCacheSetting(): Boolean {
        return try {
            val file = getSettingsFile()
            if (!file.exists()) return true
            val props = java.util.Properties()
            file.inputStream().use { props.load(it) }
            props.getProperty("cacheEnabled", "true") != "false"
        } catch (e: Exception) { true }
    }

    private fun loadThemeSetting(): MuSyncThemePreset {
        return try {
            val file = getSettingsFile()
            if (!file.exists()) return MuSyncThemePreset.NEO_GREEN
            val props = java.util.Properties()
            file.inputStream().use { props.load(it) }
            MuSyncThemePreset.fromId(props.getProperty("theme", MuSyncThemePreset.NEO_GREEN.id))
        } catch (_: Exception) {
            MuSyncThemePreset.NEO_GREEN
        }
    }

    private fun saveCacheSetting(value: Boolean) {
        try {
            val file = getSettingsFile()
            val props = java.util.Properties()
            if (file.exists()) file.inputStream().use { props.load(it) }
            props.setProperty("cacheEnabled", value.toString())
            file.outputStream().use { props.store(it, "MuSync client settings") }
        } catch (e: Exception) {
            dev.mcrib884.musync.MuSyncLog.error("Failed to save MuSync settings: ${e.message}")
        }
    }

    private fun saveThemeSetting(value: MuSyncThemePreset) {
        try {
            val file = getSettingsFile()
            val props = java.util.Properties()
            if (file.exists()) file.inputStream().use { props.load(it) }
            props.setProperty("theme", value.id)
            file.outputStream().use { props.store(it, "MuSync client settings") }
        } catch (e: Exception) {
            dev.mcrib884.musync.MuSyncLog.error("Failed to save MuSync settings: ${e.message}")
        }
    }

    private fun clearCacheFolder() {
        try {
            val folder = getCacheFolder()
            if (!folder.exists()) return
            folder.listFiles()?.forEach { file ->
                if (file.isFile) {
                    try {
                        file.delete()
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            dev.mcrib884.musync.MuSyncLog.error("Failed to clear MuSync cache: ${e.message}")
        }
    }

    private fun getCacheFolder(): File =
        File(Minecraft.getInstance().gameDirectory, "musynccache")

    private fun getLocalFolder(): File {
        val gameDir = Minecraft.getInstance().gameDirectory
        return File(gameDir, "customtracks")
    }

    private fun scanFolderTracks(folder: File): Map<String, File> {
        if (!folder.exists()) return emptyMap()

        return folder.listFiles { f ->
            f.isFile && f.extension.lowercase() in SUPPORTED_EXTENSIONS
        }?.mapNotNull { file ->
            val key = normalizeInternalName(file.name) ?: return@mapNotNull null
            key to file
        }?.toMap() ?: emptyMap()
    }

    private fun scanLocalTracks(): Map<String, File> {
        val folder = getLocalFolder().apply {
            if (!exists()) mkdirs()
        }
        return scanFolderTracks(folder).filterValues { file ->
            val size = file.length()
            if (size <= 0L || size > PacketIO.MAX_TRACK_SIZE_BYTES) {
                dev.mcrib884.musync.MuSyncLog.warn("Ignoring local custom track with invalid size: ${file.name} ($size bytes)")
                false
            } else {
                true
            }
        }
    }

    private fun sha256Of(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read > 0) digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            dev.mcrib884.musync.MuSyncLog.error("Failed to hash track ${file.name}: ${e.message}")
            null
        }
    }

    private fun hasValidTrackFile(file: File?, entry: TrackManifestEntry): Boolean {
        if (file == null || !file.isFile) return false
        val size = file.length()
        if (size <= 0L || size > PacketIO.MAX_TRACK_SIZE_BYTES || size != entry.size) return false
        if (entry.sha256.isBlank()) return true
        val hash = sha256Of(file) ?: return false
        return hash.equals(entry.sha256, ignoreCase = true)
    }

    private fun hasUsableCachedTrack(file: File?, entry: TrackManifestEntry): Boolean {
        val cacheFile = file ?: return false
        return cacheEnabled && hasValidTrackFile(cacheFile, entry)
    }

    private fun remainingTrackNames(): List<String> {
        return downloadStates.values
            .filter { !it.completed && !it.failed }
            .map { it.entry.name }
    }

    private fun refreshProgressCounters() {
        totalBytesReceived = downloadStates.values.sumOf { state ->
            if (state.completed) state.entry.size else state.bytesReceived.coerceIn(0L, state.entry.size)
        }
    }

    private fun refreshCurrentTrackCursor() {
        val nextIndex = tracksToDownload.indexOfFirst { (name, _) ->
            val state = downloadStates[name]
            state != null && !state.completed && !state.failed
        }
        if (nextIndex < 0) {
            currentDownloadIndex = tracksToDownload.size
            currentTrackChunksReceived = 0
            currentTrackTotalChunks = 0
            return
        }
        currentDownloadIndex = nextIndex
        val state = downloadStates[tracksToDownload[nextIndex].first]
        currentTrackChunksReceived = state?.chunksReceived ?: 0
        currentTrackTotalChunks = state?.totalChunks ?: 0
    }

    private fun notifyDownloadComplete() {
        Minecraft.getInstance().execute {
            val mc = Minecraft.getInstance()
            val screen = mc.screen
            if (screen is TrackDownloadScreen) {
                screen.onDownloadComplete()
            } else if (failedTracks.isNotEmpty()) {
                val next = TrackDownloadScreen()
                mc.setScreen(next)
                next.onDownloadComplete()
            }
        }
    }

    private fun finalizeDownloadState() {
        isDownloading = false
        pendingRequested.clear()
        stallRetries = 0
        lastProgressAtMs = 0L
        refreshProgressCounters()
        refreshCurrentTrackCursor()
        downloadComplete = failedTracks.isEmpty()
        if (downloadComplete) {
            dev.mcrib884.musync.MuSyncLog.info("All custom tracks downloaded and synced!")
            PacketHandler.sendToServer(MusicClientInfoPacket(MusicClientInfoPacket.Action.DOWNLOAD_SYNC_FINISHED, "", 0L))
        } else {
            dev.mcrib884.musync.MuSyncLog.warn("Custom track sync finished with ${failedTracks.size} failed track(s): ${failedTracks.joinToString(",")}")
            PacketHandler.sendToServer(MusicClientInfoPacket(MusicClientInfoPacket.Action.DOWNLOAD_SYNC_FAILED, "", failedTracks.size.toLong()))
        }
        notifyDownloadComplete()
    }

    private fun requestRemainingTracks() {
        val remaining = remainingTrackNames()
        if (remaining.isEmpty()) {
            finalizeDownloadState()
            return
        }
        pendingRequested.clear()
        pendingRequested.addAll(remaining)
        lastProgressAtMs = System.currentTimeMillis()
        PacketHandler.sendToServer(TrackRequestPacket(serverManifestVersion, remaining))
    }

    fun handleManifest(manifestVersion: Long, manifest: List<TrackManifestEntry>) {
        ensureSettingsLoaded()
        serverManifest = manifest
        serverManifestVersion = manifestVersion
        failedTracks.clear()
        pendingRequested.clear()
        downloadStates.clear()
        tracksToDownload = emptyList()
        currentDownloadIndex = 0
        currentTrackChunksReceived = 0
        currentTrackTotalChunks = 0
        totalBytesToDownload = 0L
        totalBytesReceived = 0L
        stallRetries = 0
        lastProgressAtMs = 0L
        isDownloading = false
        downloadComplete = false

        val localTracks = scanLocalTracks()
        val cachedTracks = if (cacheEnabled) scanFolderTracks(getCacheFolder()) else emptyMap()
        val missing = mutableListOf<TrackManifestEntry>()
        for (rawEntry in manifest) {
            val internalName = normalizeInternalName(rawEntry.name)
            if (internalName == null) {
                failedTracks.add(rawEntry.name)
                continue
            }
            val entry = if (internalName == rawEntry.name) rawEntry else rawEntry.copy(name = internalName)
            if (hasValidTrackFile(localTracks[internalName], entry)) continue
            if (hasUsableCachedTrack(cachedTracks[internalName], entry)) continue
            missing.add(entry)
            downloadStates[entry.name] = ManifestTrackState(entry)
        }

        tracksToDownload = missing.map { it.name to it.size }
        totalBytesToDownload = missing.sumOf { it.size }
        refreshProgressCounters()
        refreshCurrentTrackCursor()

        if (missing.isEmpty()) {
            dev.mcrib884.musync.MuSyncLog.info("All ${manifest.size} custom tracks are synced")
            downloadComplete = failedTracks.isEmpty()
            PacketHandler.sendToServer(TrackRequestPacket(serverManifestVersion, emptyList()))
            PacketHandler.sendToServer(
                MusicClientInfoPacket(
                    if (downloadComplete) MusicClientInfoPacket.Action.DOWNLOAD_SYNC_FINISHED else MusicClientInfoPacket.Action.DOWNLOAD_SYNC_FAILED,
                    "",
                    failedTracks.size.toLong()
                )
            )
            return
        }

        isDownloading = true
        downloadComplete = false
        lastProgressAtMs = System.currentTimeMillis()
        PacketHandler.sendToServer(MusicClientInfoPacket(MusicClientInfoPacket.Action.DOWNLOAD_SYNC_STARTED, "", missing.size.toLong()))
        dev.mcrib884.musync.MuSyncLog.info("Need to download ${missing.size} custom tracks (${formatSize(totalBytesToDownload)})")
        requestRemainingTracks()
    }

    fun onChunkProgress(trackName: String, chunkIndex: Int, totalChunks: Int, acceptedBytes: Long) {
        val state = downloadStates[trackName] ?: return
        if (state.completed || state.failed) return
        lastProgressAtMs = System.currentTimeMillis()
        state.totalChunks = maxOf(state.totalChunks, totalChunks)
        state.chunksReceived = maxOf(state.chunksReceived, chunkIndex + 1)
        state.bytesReceived = (state.bytesReceived + acceptedBytes).coerceAtMost(state.entry.size)
        refreshProgressCounters()
        refreshCurrentTrackCursor()
    }

    fun onTrackDownloaded(trackName: String, totalChunks: Int, tempFile: File, totalSize: Long) {
        val state = downloadStates[trackName]
        try {
            val saved = saveTrackToDisk(trackName, tempFile, totalSize, state?.entry?.sha256)
            if (state == null) {
                if (!saved) {
                    dev.mcrib884.musync.MuSyncLog.warn("Discarded transient custom track '$trackName'")
                }
                return
            }
            pendingRequested.remove(trackName)
            if (!saved) {
                state.failed = true
                failedTracks.add(trackName)
                dev.mcrib884.musync.MuSyncLog.warn("Track marked failed and skipped: $trackName")
            } else {
                state.completed = true
                state.failed = false
                state.totalChunks = maxOf(state.totalChunks, totalChunks)
                state.chunksReceived = maxOf(state.chunksReceived, totalChunks)
                state.bytesReceived = state.entry.size
                stallRetries = 0
            }
        } finally {
            try {
                if (tempFile.exists()) tempFile.delete()
            } catch (_: Exception) {}
        }
        refreshProgressCounters()
        refreshCurrentTrackCursor()
        if (remainingTrackNames().isEmpty()) {
            finalizeDownloadState()
        }
    }

    fun onTrackFailed(trackName: String, reason: String) {
        val state = downloadStates[trackName] ?: return
        pendingRequested.remove(trackName)
        state.failed = true
        failedTracks.add(trackName)
        dev.mcrib884.musync.MuSyncLog.warn("Track download failed for '$trackName': $reason")
        refreshProgressCounters()
        refreshCurrentTrackCursor()
        if (remainingTrackNames().isEmpty()) {
            finalizeDownloadState()
        }
    }

    fun onClientTick() {
        if (!isDownloading) return
        val remaining = remainingTrackNames()
        if (remaining.isEmpty()) {
            finalizeDownloadState()
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastProgressAtMs < DOWNLOAD_STALL_TIMEOUT_MS) return

        if (stallRetries < MAX_STALL_RETRIES) {
            stallRetries++
            lastProgressAtMs = now
            dev.mcrib884.musync.MuSyncLog.warn("Track download stalled, retrying remaining ${remaining.size} track(s) (attempt $stallRetries/$MAX_STALL_RETRIES)")
            requestRemainingTracks()
            return
        }

        dev.mcrib884.musync.MuSyncLog.error("Track download aborted after stall retries; remaining tracks: ${remaining.joinToString(",")}")
        remaining.forEach { name ->
            downloadStates[name]?.failed = true
            failedTracks.add(name)
        }
        finalizeDownloadState()
    }

    private fun saveTrackToDisk(trackName: String, sourceFile: File, totalSize: Long, expectedSha256: String? = null): Boolean {
        val safeName = normalizeInternalName(trackName)
        if (safeName == null) {
            dev.mcrib884.musync.MuSyncLog.warn("Refusing to save track with unsafe name: $trackName")
            return false
        }
        if (!sourceFile.isFile || totalSize <= 0L || sourceFile.length() != totalSize) {
            dev.mcrib884.musync.MuSyncLog.warn("Refusing to save track with invalid temp file: $trackName")
            return false
        }
        try {
            val stream = CustomTrackPlayer.prepareStream(sourceFile)
            if (stream == null) {
                dev.mcrib884.musync.MuSyncLog.warn("Refusing to save track because content is not decodable: $trackName")
                return false
            }
            stream.close()

            if (!expectedSha256.isNullOrBlank()) {
                val actualSha256 = sha256Of(sourceFile)
                if (actualSha256 == null || !actualSha256.equals(expectedSha256, ignoreCase = true)) {
                    dev.mcrib884.musync.MuSyncLog.error("SHA-256 mismatch for track $trackName: expected $expectedSha256, got $actualSha256")
                    return false
                }
            }

            if (cacheEnabled) {
                try {
                    val cf = getCacheFolder()
                    if (!cf.exists()) cf.mkdirs()
                    val cacheFile = File(cf, safeName)
                    if (isInsideFolder(cacheFile, cf)) {
                        sourceFile.copyTo(cacheFile, overwrite = true)
                        dev.mcrib884.musync.MuSyncLog.info("Saved synced custom track to cache: ${cacheFile.name} ($totalSize bytes)")
                    }
                } catch (e2: Exception) {
                    dev.mcrib884.musync.MuSyncLog.error("Failed to cache track $trackName: ${e2.message}")
                    return false
                }
            } else {
                CustomTrackCache.put(safeName, sourceFile.readBytes())
                dev.mcrib884.musync.MuSyncLog.info("Loaded synced custom track into memory: $safeName ($totalSize bytes)")
            }
            return true
        } catch (e: Exception) {
            dev.mcrib884.musync.MuSyncLog.error("Failed to save track $trackName: ${e.message}")
            return false
        }
    }

    private fun isInsideFolder(file: File, folder: File): Boolean {
        return try {
            file.canonicalFile.toPath().startsWith(folder.canonicalFile.toPath())
        } catch (_: Exception) {
            false
        }
    }

    fun currentTrackName(): String {
        return if (currentDownloadIndex in tracksToDownload.indices) {
            displayTrackName(tracksToDownload[currentDownloadIndex].first)
        } else ""
    }

    fun currentTrackSize(): Long {
        return if (currentDownloadIndex in tracksToDownload.indices) {
            tracksToDownload[currentDownloadIndex].second
        } else 0L
    }

    fun reset() {
        serverManifest = emptyList()
        serverManifestVersion = -1L
        downloadStates.clear()
        tracksToDownload = emptyList()
        currentDownloadIndex = 0
        currentTrackChunksReceived = 0
        currentTrackTotalChunks = 0
        totalBytesToDownload = 0
        totalBytesReceived = 0
        failedTracks.clear()
        pendingRequested.clear()
        lastProgressAtMs = 0L
        stallRetries = 0
        isDownloading = false
        downloadComplete = false
    }

    fun getServerCustomTrackNames(): List<String> {
        return serverManifest
            .mapNotNull { normalizeInternalName(it.name) }
            .distinct()
            .sorted()
    }

    fun getFailedTracks(): List<String> = failedTracks.toList()

    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}
