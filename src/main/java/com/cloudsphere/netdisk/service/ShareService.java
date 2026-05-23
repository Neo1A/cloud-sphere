package com.cloudsphere.netdisk.service;

import com.cloudsphere.netdisk.dto.ShareCreateDTO;
import com.cloudsphere.netdisk.vo.ShareVO;

public interface ShareService {
    ShareVO createShare(ShareCreateDTO dto);
}