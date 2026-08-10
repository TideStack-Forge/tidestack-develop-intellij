package com.ouroboros.idea.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "OuroborosSettings",
    storages = [Storage("OuroborosPlugin.xml")]
)
@Service
class OuroborosSettings : PersistentStateComponent<OuroborosSettings> {
    /** Legacy fields kept only to migrate project URL, app name, and dev key into TideStack Develop Config. */
    var platformUrl: String = ""
    var appName: String = ""
    var profileId: String = ""
    var devKey: String = ""
    var debugKey: String = ""
    var containerId: String = ""

    override fun getState(): OuroborosSettings = this

    override fun loadState(state: OuroborosSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): OuroborosSettings = service()
    }
}
