package com.lhzkml.DayWage.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lhzkml.DayWage.DayWageApp
import com.lhzkml.DayWage.ui.screens.BatchViewModel
import com.lhzkml.DayWage.ui.screens.EntryViewModel
import com.lhzkml.DayWage.ui.screens.GoalsViewModel
import com.lhzkml.DayWage.ui.screens.MineViewModel
import com.lhzkml.DayWage.ui.screens.RecordViewModel
import com.lhzkml.DayWage.ui.screens.ReportViewModel
import com.lhzkml.DayWage.ui.screens.SalaryViewModel
import com.lhzkml.DayWage.ui.screens.StatsViewModel
import com.lhzkml.DayWage.ui.screens.YearStatsViewModel

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { RecordViewModel(container().repository, container().settings) }
        initializer { EntryViewModel(container().repository, container().settings) }
        initializer { StatsViewModel(container().repository) }
        initializer { GoalsViewModel(container().repository) }
        initializer {
            MineViewModel(container().repository, container().settings) { container().resetRecords() }
        }
        initializer { SalaryViewModel(container().repository, container().settings) }
        initializer { BatchViewModel(container().repository, container().settings) }
        initializer { ReportViewModel(container().repository) }
        initializer { YearStatsViewModel(container().repository) }
    }
}

private fun CreationExtras.container() =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DayWageApp).container
