package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.viewmodel.FoliaViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("folia", appName)
  }

  @Test
  fun `init viewModel and database`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = FoliaViewModel(app)
    assertNotNull(vm)
  }

  @Test
  fun `test fragmented arabic spacing healing`() {
    val raw = "ل ا إ ل ه إ ل ا ا ل ل ه"
    val fixed = com.example.domain.NormalizationHelper.fixFragmentedArabicSpacing(raw)
    assertEquals("لا إله إلا الله", fixed)

    val prefixes = "و ق ا ل ف ك ا ن"
    val fixedPrefixes = com.example.domain.NormalizationHelper.fixFragmentedArabicSpacing(prefixes)
    assertEquals("وقال فكان", fixedPrefixes)
  }

  @Test
  fun `test classical manuscript phrase healing`() {
    val corruptedBasmalah = "بسم الله الر حمن الر حيم"
    val healed = com.example.domain.NormalizationHelper.healClassicalArabicPhrases(corruptedBasmalah)
    assertEquals("بسم الله الرحمن الرحيم", healed)
  }
}
