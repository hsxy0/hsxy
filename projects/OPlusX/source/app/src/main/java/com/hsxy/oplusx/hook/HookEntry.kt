package com.hsxy.oplusx.hook

import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import com.hsxy.oplusx.hook.systemui.StatusBarProbeHook

@InjectYukiHookWithXposed(entryClassName = "oplusx")
class HookEntry : IYukiHookXposedInit {
    override fun onInit() = configs {
        debugLog {
            tag = "OPlusX"
            isEnable = true
            isRecord = true
        }
        isDebug = true
        isEnableDataChannel = true
    }

    override fun onHook() = encase {
        loadApp(hooker = StatusBarProbeHook())
    }
}
