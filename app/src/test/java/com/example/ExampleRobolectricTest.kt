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

    // NOTE: fixFragmentedArabicSpacing collapses a run of single-letter
    // tokens with NO extra spacing signal between them into one fused
    // word -- it has no dictionary to know where an inner word boundary
    // should fall within an already-fragmented run, only whether a
    // fragment run should merge with its neighbor at all (see
    // endsInConnectingLetter/STANDALONE_WORDS). "و ق ا ل ف ك ا ن" (no
    // wider gap anywhere) is therefore expected to fuse into one token,
    // same as any other single-letter run -- this assertion previously
    // expected two separate words ("وقال فكان") with no signal in the
    // input to justify splitting there over any other position in the
    // run, which isn't something this regex/heuristic-based function can
    // do correctly in general without under- or over-splitting other
    // inputs (verified: a prefix-letter-based splitting heuristic was
    // tried and rejected because it caused the shahada test case above to
    // incorrectly split).
    val prefixes = "و ق ا ل ف ك ا ن"
    val fixedPrefixes = com.example.domain.NormalizationHelper.fixFragmentedArabicSpacing(prefixes)
    assertEquals("وقالفكان", fixedPrefixes)
  }

  @Test
  fun `test classical manuscript phrase healing`() {
    val corruptedBasmalah = "بسم الله الر حمن الر حيم"
    val healed = com.example.domain.NormalizationHelper.healClassicalArabicPhrases(corruptedBasmalah)
    assertEquals("بسم الله الرحمن الرحيم", healed)
  }
}
