package app.pulse.monitor.presentation.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.pulse.monitor.data.db.WebSiteEntry
import app.pulse.monitor.data.model.WebSiteStatus
import app.pulse.monitor.data.repository.WebSiteEntryRepository
import app.pulse.monitor.utils.Constants
import app.pulse.monitor.utils.SharedPrefsManager
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: WebSiteEntryRepository = WebSiteEntryRepository(application)
    private val allWebSiteEntryList: LiveData<List<WebSiteEntry>> =
        repository.getAllWebSiteEntryList()
    private val webSiteStatusList: MutableLiveData<List<WebSiteStatus>> = MutableLiveData()
    private val webSiteStatus: MutableLiveData<WebSiteStatus> = MutableLiveData()
    private val currentRefreshingUrl: MutableLiveData<String?> = MutableLiveData()

    fun saveWebSiteEntry(webSiteEntry: WebSiteEntry) {
        repository.saveWebSiteEntry(webSiteEntry)
    }

    fun updateWebSiteEntry(webSiteEntry: WebSiteEntry) {
        repository.updateWebSiteEntry(webSiteEntry)
    }

    fun deleteWebSiteEntry(webSiteEntry: WebSiteEntry) {
        repository.deleteWebSiteEntry(webSiteEntry)
    }

    fun getWebSiteEntryList(): LiveData<List<WebSiteEntry>> {
        return allWebSiteEntryList
    }

    fun getAllWebSiteStatusList(): LiveData<List<WebSiteStatus>> {
        return webSiteStatusList
    }

    fun getWebSiteStatus(): LiveData<WebSiteStatus> {
        return webSiteStatus
    }

    fun getCurrentRefreshingUrl(): LiveData<String?> = currentRefreshingUrl

    fun checkWebSiteStatus() {
        viewModelScope.launch {
            webSiteStatusList.value = repository.checkWebSiteStatus(onEntryStart = { entry ->
                currentRefreshingUrl.postValue(entry.url)
            })
            currentRefreshingUrl.postValue(null)
            app.pulse.monitor.worker.CheckAlarmScheduler.scheduleNext(getApplication())
        }
    }

    fun getWebSiteStatus(webSiteEntry: WebSiteEntry) {
        viewModelScope.launch {
            currentRefreshingUrl.postValue(webSiteEntry.url)
            webSiteStatus.value = repository.getWebsiteStatus(webSiteEntry)
            currentRefreshingUrl.postValue(null)
        }
    }

    fun addDefaultData() {
        if (!SharedPrefsManager.customPrefs.getBoolean(Constants.IS_ADDED_DEFAULT_DATA, false)) {
            repository.addDefaultData()
        } else {
            repository.replaceLegacyDemoEntries()
        }
    }

}