package com.example.pikpak;

import java.util.List;
import com.example.pikpak.PikPakModels.*;

/** PikPak-specific outbound port. Implementations own HTTP, verification and response conversion. */
public interface PikPakShareGateway {
    Page list(PikPakShareLink share, String passcode, String parentId, String cursor);
    /** Must verify that fileId belongs to this share; never resolve an arbitrary user-supplied URL. */
    List<Media> resolve(PikPakShareLink share, String passcode, String fileId);
}
