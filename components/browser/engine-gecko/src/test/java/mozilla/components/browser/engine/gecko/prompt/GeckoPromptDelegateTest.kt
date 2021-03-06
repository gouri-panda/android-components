/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.browser.engine.gecko.prompt

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import mozilla.components.browser.engine.gecko.GeckoEngineSession
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.prompt.Choice
import mozilla.components.concept.engine.prompt.PromptRequest
import mozilla.components.concept.engine.prompt.PromptRequest.MultipleChoice
import mozilla.components.concept.engine.prompt.PromptRequest.SingleChoice
import mozilla.components.concept.storage.Login
import mozilla.components.support.ktx.kotlin.toDate
import mozilla.components.support.test.any
import mozilla.components.support.test.mock
import mozilla.components.support.test.robolectric.testContext
import mozilla.components.support.test.whenever
import mozilla.components.test.ReflectionUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.never
import org.mozilla.gecko.util.GeckoBundle
import org.mozilla.geckoview.Autocomplete
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PromptDelegate.DateTimePrompt.Type.DATE
import org.mozilla.geckoview.GeckoSession.PromptDelegate.DateTimePrompt.Type.DATETIME_LOCAL
import org.mozilla.geckoview.GeckoSession.PromptDelegate.DateTimePrompt.Type.MONTH
import org.mozilla.geckoview.GeckoSession.PromptDelegate.DateTimePrompt.Type.TIME
import org.mozilla.geckoview.GeckoSession.PromptDelegate.DateTimePrompt.Type.WEEK
import org.mozilla.geckoview.GeckoSession.PromptDelegate.FilePrompt.Capture.ANY
import org.mozilla.geckoview.GeckoSession.PromptDelegate.FilePrompt.Capture.NONE
import org.mozilla.geckoview.GeckoSession.PromptDelegate.FilePrompt.Capture.USER
import java.io.FileInputStream
import java.security.InvalidParameterException
import java.util.Calendar
import java.util.Calendar.YEAR
import java.util.Date

typealias GeckoChoice = GeckoSession.PromptDelegate.ChoicePrompt.Choice
typealias GECKO_AUTH_LEVEL = GeckoSession.PromptDelegate.AuthPrompt.AuthOptions.Level
typealias GECKO_PROMPT_CHOICE_TYPE = GeckoSession.PromptDelegate.ChoicePrompt.Type
typealias GECKO_AUTH_FLAGS = GeckoSession.PromptDelegate.AuthPrompt.AuthOptions.Flags
typealias GECKO_PROMPT_FILE_TYPE = GeckoSession.PromptDelegate.FilePrompt.Type
typealias AC_AUTH_METHOD = PromptRequest.Authentication.Method
typealias AC_AUTH_LEVEL = PromptRequest.Authentication.Level

@RunWith(AndroidJUnit4::class)
class GeckoPromptDelegateTest {

    private lateinit var runtime: GeckoRuntime

    @Before
    fun setup() {
        runtime = mock()
        whenever(runtime.settings).thenReturn(mock())
    }

    @Test
    fun `onChoicePrompt called with CHOICE_TYPE_SINGLE must provide a SingleChoice PromptRequest`() {
        val mockSession = GeckoEngineSession(runtime)
        var promptRequestSingleChoice: PromptRequest = MultipleChoice(arrayOf()) {}
        var confirmWasCalled = false
        val gecko = GeckoPromptDelegate(mockSession)
        val geckoChoice = object : GeckoChoice() {}
        val geckoPrompt = GeckoChoicePrompt(
            "title",
            "message",
            GECKO_PROMPT_CHOICE_TYPE.SINGLE,
            arrayOf(geckoChoice)
        )

        mockSession.register(object : EngineSession.Observer {
            override fun onPromptRequest(promptRequest: PromptRequest) {
                promptRequestSingleChoice = promptRequest
            }
        })

        val geckoResult = gecko.onChoicePrompt(mock(), geckoPrompt)

        geckoResult!!.accept {
            confirmWasCalled = true
        }

        assertTrue(promptRequestSingleChoice is SingleChoice)
        val request = promptRequestSingleChoice as SingleChoice

        request.onConfirm(request.choices.first())
        assertTrue(confirmWasCalled)

        confirmWasCalled = false
        request.onConfirm(request.choices.first())

        assertFalse(confirmWasCalled)
    }

    @Test
    fun `onChoicePrompt called with CHOICE_TYPE_MULTIPLE must provide a MultipleChoice PromptRequest`() {
        val mockSession = GeckoEngineSession(runtime)
        var promptRequestSingleChoice: PromptRequest = SingleChoice(arrayOf()) {}
        var confirmWasCalled = false
        val gecko = GeckoPromptDelegate(mockSession)
        val mockGeckoChoice = object : GeckoChoice() {}
        val geckoPrompt = GeckoChoicePrompt(
            "title",
            "message",
            GECKO_PROMPT_CHOICE_TYPE.MULTIPLE,
            arrayOf(mockGeckoChoice)
        )

        mockSession.register(object : EngineSession.Observer {
            override fun onPromptRequest(promptRequest: PromptRequest) {
                promptRequestSingleChoice = promptRequest
            }
        })

        val geckoResult = gecko.onChoicePrompt(mock(), geckoPrompt)

        geckoResult!!.accept {
            confirmWasCalled = true
        }

        assertTrue(promptRequestSingleChoice is MultipleChoice)

        (promptRequestSingleChoice as MultipleChoice).onConfirm(arrayOf())
        assertTrue(confirmWasCalled)

        confirmWasCalled = false
        (promptRequestSingleChoice as MultipleChoice).onConfirm(arrayOf())

        assertFalse(confirmWasCalled)
    }

    @Test
    fun `onChoicePrompt called with CHOICE_TYPE_MENU must provide a MenuChoice PromptRequest`() {
        val mockSession = GeckoEngineSession(runtime)
        var promptRequestSingleChoice: PromptRequest = PromptRequest.MenuChoice(arrayOf()) {}
        var confirmWasCalled = false
        val gecko = GeckoPromptDelegate(mockSession)
        val geckoChoice = object : GeckoChoice() {}
        val geckoPrompt = GeckoChoicePrompt(
            "title",
            "message",
            GECKO_PROMPT_CHOICE_TYPE.MENU,
            arrayOf(geckoChoice)
        )

        mockSession.register(
            object : EngineSession.Observer {
                override fun onPromptRequest(promptRequest: PromptRequest) {
                    promptRequestSingleChoice = promptRequest
                }
            })

        val geckoResult = gecko.onChoicePrompt(mock(), geckoPrompt)
        geckoResult!!.accept {
            confirmWasCalled = true
        }

        assertTrue(promptRequestSingleChoice is PromptRequest.MenuChoice)
        val request = promptRequestSingleChoice as PromptRequest.MenuChoice

        request.onConfirm(request.choices.first())
        assertTrue(confirmWasCalled)

        confirmWasCalled = false
        request.onConfirm(request.choices.first())

        assertFalse(confirmWasCalled)
    }

    @Test(expected = InvalidParameterException::class)
    fun `calling onChoicePrompt with not valid Gecko ChoiceType will throw an exception`() {
        val promptDelegate = GeckoPromptDelegate(mock())
        val geckoPrompt = GeckoChoicePrompt(
            "title",
            "message",
            -1,
            arrayOf()
        )
        promptDelegate.onChoicePrompt(mock(), geckoPrompt)
    }

    @Test
    fun `onAlertPrompt must provide an alert PromptRequest`() {
        val mockSession = GeckoEngineSession(runtime)
        var alertRequest: PromptRequest? = null
        var dismissWasCalled = false

        val promptDelegate = GeckoPromptDelegate(mockSession)

        mockSession.register(object : EngineSession.Observer {
            override fun onPromptRequest(promptRequest: PromptRequest) {
                alertRequest = promptRequest
            }
        })

        val geckoResult = promptDelegate.onAlertPrompt(mock(), GeckoAlertPrompt())
        geckoResult.accept {
            dismissWasCalled = true
        }
        assertTrue(alertRequest is PromptRequest.Alert)

        (alertRequest as PromptRequest.Alert).onDismiss()
        assertTrue(dismissWasCalled)

        assertEquals((alertRequest as PromptRequest.Alert).title, "title")
        assertEquals((alertRequest as PromptRequest.Alert).message, "message")
    }

    @Test
    fun `toIdsArray must convert an list of choices to array of id strings`() {
        val choices = arrayOf(Choice(id = "0", label = ""), Choice(id = "1", label = ""))
        val ids = choices.toIdsArray()
        ids.forEachIndexed { index, item ->
            assertEquals("$index", item)
        }
    }

    @Test
    fun `onDateTimePrompt called with DATETIME_TYPE_DATE must provide a date PromptRequest`() {
        val mockSession = GeckoEngineSession(runtime)
        var dateRequest: PromptRequest? = null
        var confirmCalled = false
        var onClearPicker = false
        var geckoPrompt = GeckoDateTimePrompt("title", DATE, "", "", "")

        val promptDelegate = GeckoPromptDelegate(mockSession)
        mockSession.register(object : EngineSession.Observer {
            override fun onPromptRequest(promptRequest: PromptRequest) {
                dateRequest = promptRequest
            }
        })

        var geckoResult = promptDelegate.onDateTimePrompt(mock(), geckoPrompt)
        geckoResult!!.accept {
            confirmCalled = true
        }

        assertTrue(dateRequest is PromptRequest.TimeSelection)
        (dateRequest as PromptRequest.TimeSelection).onConfirm(Date())
        assertTrue(confirmCalled)
        assertEquals((dateRequest as PromptRequest.TimeSelection).title, "title")

        confirmCalled = false
        (dateRequest as PromptRequest.TimeSelection).onConfirm(Date())

        assertFalse(confirmCalled)

        geckoPrompt = GeckoDateTimePrompt("title", DATE, "", "", "")
        geckoResult = promptDelegate.onDateTimePrompt(mock(), geckoPrompt)
        geckoResult!!.accept {
            onClearPicker = true
        }

        (dateRequest as PromptRequest.TimeSelection).onClear()
        assertTrue(onClearPicker)
    }

    @Test
    fun `onDateTimePrompt DATETIME_TYPE_DATE with date parameters must format dates correctly`() {
        val mockSession = GeckoEngineSession(runtime)
        var timeSelectionRequest: PromptRequest.TimeSelection? = null
        var geckoDate: String? = null

        val geckoPrompt =
            GeckoDateTimePrompt(
                title = "title",
                type = DATE,
                defaultValue = "2019-11-29",
                minValue = "2019-11-28",
                maxValue = "2019-11-30"
            )
        val promptDelegate = GeckoPromptDelegate(mockSession)
        mockSession.register(object : EngineSession.Observer {
            override fun onPromptRequest(promptRequest: PromptRequest) {
                timeSelectionRequest = promptRequest as PromptRequest.TimeSelection
            }
        })

        val geckoResult = promptDelegate.onDateTimePrompt(mock(), geckoPrompt)
        geckoResult!!.accept {
            geckoDate = geckoPrompt.getGeckoResult()["datetime"].toString()
        }

        assertNotNull(timeSelectionRequest)
        with(timeSelectionRequest!!) {
            assertEquals(initialDate, "2019-11-29".toDate("yyyy-MM-dd"))
            assertEquals(minimumDate, "2019-11-28".toDate("yyyy-MM-dd"))
            assertEquals(maximumDate, "2019-11-30".toDate("yyyy-MM-dd"))
        }
        val selectedDate = "2019-11-28".toDate("yyyy-MM-dd")
        (timeSelectionRequest as PromptRequest.TimeSelection).onConfirm(selectedDate)
        assertNotNull(geckoDate?.toDate("yyyy-MM-dd")?.equals(selectedDate))
        assertEquals((timeSelectionRequest as PromptRequest.TimeSelection).title, "title")
    }

    @Test
    fun `onDateTimePrompt called with DATETIME_TYPE_MONTH must provide a date PromptRequest`() {
        val mockSession = GeckoEngineSession(runtime)
        var dateRequest: PromptRequest? = null
        var confirmCalled = false

        val promptDelegate = GeckoPromptDelegate(mockSession)
        mockSession.register(object : EngineSession.Observer {
            override fun onPromptRequest(promptRequest: PromptRequest) {
                dateRequest = promptRequest
            }
        })
        val geckoPrompt = GeckoDateTimePrompt(type = MONTH)

        val geckoResult = promptDelegate.onDateTimePrompt(mock(), geckoPrompt)
        geckoResult!!.accept {
            confirmCalled = true
        }
        assertTrue(dateRequest is PromptRequest.TimeSelection)
        (dateRequest as PromptRequest.TimeSelection).onConfirm(Date())
        assertTrue(confirmCalled)
        assertEquals((dateRequest as PromptRequest.TimeSelection).title, "title")
    }

    @Test
    fun `onDateTimePrompt DATETIME_TYPE_MONTH with date parameters must format dates correctly`() {
        val mockSession = GeckoEngineSession(runtime)
        var timeSelectionRequest: PromptRequest.TimeSelection? = null
        var geckoDate: String? = null

        val promptDelegate = GeckoPromptDelegate(mockSession)
        mockSession.register(object : EngineSession.Observer {
            override fun onPromptRequest(promptRequest: PromptRequest) {
                timeSelectionRequest = promptRequest as PromptRequest.TimeSelection
            }
        })
        val geckoPrompt = GeckoDateTimePrompt(
            title = "title",
            type = MONTH,
            defaultValue = "2019-11",
            minValue = "2019-11",
            maxValue = "2019-11"
        )
        val geckoResult = promptDelegate.onDateTimePrompt(mock(), geckoPrompt)
        geckoResult!!.accept {
            geckoDate = geckoPrompt.getGeckoResult()["datetime"].toString()
        }

        assertNotNull(timeSelectionRequest)
        with(timeSelectionRequest!!) {
            assertEquals(initialDate, "2019-11".toDate("yyyy-MM"))
            assertEquals(minimumDate, "2019-11".toDate("yyyy-MM"))
            assertEquals(maximumDate, "2019-11".toDate("yyyy-MM"))
        }
        val selectedDate = "2019-11".toDate("yyyy-MM")
        (timeSelectionRequest as PromptRequest.TimeSelection).onConfirm(selectedDate)
        assertNotNull(geckoDate?.toDate("yyyy-MM")?.equals(selectedDate))
        assertEquals((timeSelectionRequest as PromptRequest.TimeSelection).title, "title")
    }

    @Test
    fun `onDateTimePrompt called with DATETIME_TYPE_WEEK must provide a date PromptRequest`() {
        val mockSession = GeckoEngineSession(runtime)
        var dateRequest: PromptRequest? = null
        var confirmCalled = false
        val promptDelegate = GeckoPromptDelegate(mockSession)
        mockSession.register(object : EngineSession.Observer {
            override fun onPromptRequest(promptRequest: PromptRequest) {
                dateRequest = promptRequest
            }
        })
        val geckoPrompt = GeckoDateTimePrompt(type = WEEK)

        val geckoResult = promptDelegate.onDateTimePrompt(mock(), geckoPrompt)
        geckoResult!!.accept {
            confirmCalled = true
        }

        assertTrue(dateRequest is PromptRequest.TimeSelection)
        (dateRequest as PromptRequest.TimeSelection).onConfirm(Date())
        assertTrue(confirmCalled)
        assertEquals((dateRequest as PromptRequest.TimeSelection).title, "title")
    }

    @Test
    fun `onDateTimePrompt DATETIME_TYPE_WEEK with date parameters must format dates correctly`() {
        val mockSession = GeckoEngineSession(runtime)
        var timeSelectionRequest: PromptRequest.TimeSelection? = null
        var geckoDate: String? = null
        val promptDelegate = GeckoPromptDelegate(mockSession)
        mockSession.register(object : EngineSession.Observer {
            override fun onPromptRequest(promptRequest: PromptRequest) {
                timeSelectionRequest = promptRequest as PromptRequest.TimeSelection
            }
        })

        val geckoPrompt = GeckoDateTimePrompt(
            title = "title",
            type = WEEK,
            defaultValue = "2018-W18",
            minValue = "2018-W18",
            maxValue = "2018-W26"
        )
        val geckoResult = promptDelegate.onDateTimePrompt(mock(), geckoPrompt)
        geckoResult!!.accept {
            geckoDate = geckoPrompt.getGeckoResult()["datetime"].toString()
        }

        assertNotNull(timeSelectionRequest)
        with(timeSelectionRequest!!) {
            assertEquals(initialDate, "2018-W18".toDate("yyyy-'W'ww"))
            assertEquals(minimumDate, "2018-W18".toDate("yyyy-'W'ww"))
            assertEquals(maximumDate, "2018-W26".toDate("yyyy-'W'ww"))
        }
        val selectedDate = "2018-W26".toDate("yyyy-'W'ww")
        (timeSelectionRequest as PromptRequest.TimeSelection).onConfirm(selectedDate)
        assertNotNull(geckoDate?.toDate("yyyy-'W'ww")?.equals(selectedDate))
        assertEquals((timeSelectionRequest as PromptRequest.TimeSelection).title, "title")
    }

    @Test
    fun `onDateTimePrompt called with DATETIME_TYPE_TIME must provide a TimeSelection PromptRequest`() {
        val mockSession = GeckoEngineSession(runtime)
        var dateRequest: PromptRequest? = null
        var confirmCalled = false

        val promptDelegate = GeckoPromptDelegate(mockSession)
        mockSession.register(object : EngineSession.Observer {
            override fun onPromptRequest(promptRequest: PromptRequest) {
                dateRequest = promptRequest
            }
        })
        val geckoPrompt = GeckoDateTimePrompt(type = TIME)

        val geckoResult = promptDelegate.onDateTimePrompt(mock(), geckoPrompt)
        geckoResult!!.accept {
            confirmCalled = true
        }

        assertTrue(dateRequest is PromptRequest.TimeSelection)
        (dateRequest as PromptRequest.TimeSelection).onConfirm(Date())
        assertTrue(confirmCalled)
        assertEquals((dateRequest as PromptRequest.TimeSelection).title, "title")
    }

    @Test
    fun `onDateTimePrompt DATETIME_TYPE_TIME with time parameters must format time correctly`() {
        val mockSession = GeckoEngineSession(runtime)
        var timeSelectionRequest: PromptRequest.TimeSelection? = null
        var geckoDate: String? = null

        val promptDelegate = GeckoPromptDelegate(mockSession)
        mockSession.register(object : EngineSession.Observer {
            override fun onPromptRequest(promptRequest: PromptRequest) {
                timeSelectionRequest = promptRequest as PromptRequest.TimeSelection
            }
        })

        val geckoPrompt = GeckoDateTimePrompt(
            title = "title",
            type = TIME,
            defaultValue = "17:00",
            minValue = "9:00",
            maxValue = "18:00"
        )
        val geckoResult = promptDelegate.onDateTimePrompt(mock(), geckoPrompt)
        geckoResult!!.accept {
            geckoDate = geckoPrompt.getGeckoResult()["datetime"].toString()
        }

        assertNotNull(timeSelectionRequest)
        with(timeSelectionRequest!!) {
            assertEquals(initialDate, "17:00".toDate("HH:mm"))
            assertEquals(minimumDate, "9:00".toDate("HH:mm"))
            assertEquals(maximumDate, "18:00".toDate("HH:mm"))
        }
        val selectedDate = "17:00".toDate("HH:mm")
        (timeSelectionRequest as PromptRequest.TimeSelection).onConfirm(selectedDate)
        assertNotNull(geckoDate?.toDate("HH:mm")?.equals(selectedDate))
        assertEquals((timeSelectionRequest as PromptRequest.TimeSelection).title, "title")
    }

    @Test
    fun `onDateTimePrompt called with DATETIME_TYPE_DATETIME_LOCAL must provide a TimeSelection PromptRequest`() {
        val mockSession = GeckoEngineSession(runtime)
        var dateRequest: PromptRequest? = null
        var confirmCalled = false

        val promptDelegate = GeckoPromptDelegate(mockSession)
        mockSession.register(object : EngineSession.Observer {
            override fun onPromptRequest(promptRequest: PromptRequest) {
                dateRequest = promptRequest
            }
        })
        val geckoResult =
            promptDelegate.onDateTimePrompt(mock(), GeckoDateTimePrompt(type = DATETIME_LOCAL))
        geckoResult!!.accept {
            confirmCalled = true
        }

        assertTrue(dateRequest is PromptRequest.TimeSelection)
        (dateRequest as PromptRequest.TimeSelection).onConfirm(Date())
        assertTrue(confirmCalled)
        assertEquals((dateRequest as PromptRequest.TimeSelection).title, "title")
    }

    @Test
    fun `onDateTimePrompt DATETIME_TYPE_DATETIME_LOCAL with date parameters must format time correctly`() {
        val mockSession = GeckoEngineSession(runtime)
        var timeSelectionRequest: PromptRequest.TimeSelection? = null
        var geckoDate: String? = null
        val promptDelegate = GeckoPromptDelegate(mockSession)
        mockSession.register(object : EngineSession.Observer {
            override fun onPromptRequest(promptRequest: PromptRequest) {
                timeSelectionRequest = promptRequest as PromptRequest.TimeSelection
            }
        })
        val geckoPrompt = GeckoDateTimePrompt(
            title = "title",
            type = DATETIME_LOCAL,
            defaultValue = "2018-06-12T19:30",
            minValue = "2018-06-07T00:00",
            maxValue = "2018-06-14T00:00"
        )
        val geckoResult = promptDelegate.onDateTimePrompt(mock(), geckoPrompt)
        geckoResult!!.accept {
            geckoDate = geckoPrompt.getGeckoResult()["datetime"].toString()
        }

        assertNotNull(timeSelectionRequest)
        with(timeSelectionRequest!!) {
            assertEquals(initialDate, "2018-06-12T19:30".toDate("yyyy-MM-dd'T'HH:mm"))
            assertEquals(minimumDate, "2018-06-07T00:00".toDate("yyyy-MM-dd'T'HH:mm"))
            assertEquals(maximumDate, "2018-06-14T00:00".toDate("yyyy-MM-dd'T'HH:mm"))
        }
        val selectedDate = "2018-06-12T19:30".toDate("yyyy-MM-dd'T'HH:mm")
        (timeSelectionRequest as PromptRequest.TimeSelection).onConfirm(selectedDate)
        assertNotNull(geckoDate?.toDate("yyyy-MM-dd'T'HH:mm")?.equals(selectedDate))
        assertEquals((timeSelectionRequest as PromptRequest.TimeSelection).title, "title")
    }

    @Test(expected = InvalidParameterException::class)
    fun `Calling onDateTimePrompt with invalid DatetimeType will throw an exception`() {
        val promptDelegate = GeckoPromptDelegate(mock())
        promptDelegate.onDateTimePrompt(
            mock(),
            GeckoDateTimePrompt(
                type = 13223,
                defaultValue = "17:00",
                minValue = "9:00",
                maxValue = "18:00"
            )
        )
    }

    @Test
    fun `date to string`() {
        val date = Date()

        var dateString = date.toString()
        assertNotNull(dateString.isEmpty())

        dateString = date.toString("yyyy")
        val calendar = Calendar.getInstance()
        calendar.time = date
        val year = calendar[YEAR].toString()
        assertEquals(dateString, year)
    }

    @Test
    fun `Calling onFilePrompt must provide a FilePicker PromptRequest`() {
        val context = spy(testContext)
        val contentResolver = spy(context.contentResolver)
        val mockSession = GeckoEngineSession(runtime)
        var onSingleFileSelectedWasCalled = false
        var onMultipleFilesSelectedWasCalled = false
        var onDismissWasCalled = false
        val mockUri: Uri = mock()

        doReturn(contentResolver).`when`(context).contentResolver
        doReturn(mock<FileInputStream>()).`when`(contentResolver).openInputStream(any())

        var filePickerRequest: PromptRequest.File = mock()

        val promptDelegate = spy(GeckoPromptDelegate(mockSession))

        // Prevent the file from being copied
        doReturn(0L).`when`(promptDelegate).copyFile(any(), any())

        mockSession.register(object : EngineSession.Observer {
            override fun onPromptRequest(promptRequest: PromptRequest) {
                filePickerRequest = promptRequest as PromptRequest.File
            }
        })
        var geckoPrompt = GeckoFilePrompt(type = GECKO_PROMPT_FILE_TYPE.SINGLE, capture = NONE)

        var geckoResult = promptDelegate.onFilePrompt(mock(), geckoPrompt)
        geckoResult!!.accept {
            onSingleFileSelectedWasCalled = true
        }

        filePickerRequest.onSingleFileSelected(context, mockUri)
        assertTrue(onSingleFileSelectedWasCalled)

        onSingleFileSelectedWasCalled = false
        filePickerRequest.onSingleFileSelected(context, mockUri)

        assertFalse(onSingleFileSelectedWasCalled)

        geckoPrompt = GeckoFilePrompt(type = GECKO_PROMPT_FILE_TYPE.MULTIPLE, capture = ANY)
        geckoResult = promptDelegate.onFilePrompt(mock(), geckoPrompt)
        geckoResult!!.accept {
            onMultipleFilesSelectedWasCalled = true
        }

        filePickerRequest.onMultipleFilesSelected(context, arrayOf(mockUri))
        assertTrue(onMultipleFilesSelectedWasCalled)

        onMultipleFilesSelectedWasCalled = false
        filePickerRequest.onMultipleFilesSelected(context, arrayOf(mockUri))

        assertFalse(onMultipleFilesSelectedWasCalled)

        geckoPrompt = GeckoFilePrompt(type = GECKO_PROMPT_FILE_TYPE.SINGLE, capture = NONE)
        geckoResult = promptDelegate.onFilePrompt(mock(), geckoPrompt)
        geckoResult!!.accept {
            onDismissWasCalled = true
        }

        filePickerRequest.onDismiss()
        assertTrue(onDismissWasCalled)

        assertTrue(filePickerRequest.mimeTypes.isEmpty())
        assertFalse(filePickerRequest.isMultipleFilesSelection)
        assertEquals(PromptRequest.File.FacingMode.NONE, filePickerRequest.captureMode)

        promptDelegate.onFilePrompt(
            mock(),
            GeckoFilePrompt(type = GECKO_PROMPT_FILE_TYPE.MULTIPLE, capture = USER)
        )

        assertTrue(filePickerRequest.isMultipleFilesSelection)
        assertEquals(
            PromptRequest.File.FacingMode.FRONT_CAMERA,
            filePickerRequest.captureMode
        )
    }

    @Test
    fun `Calling onLoginSave must provide an SaveLoginPrompt PromptRequest`() {
        val mockSession = GeckoEngineSession(runtime)
        var onLoginSaved = false
        var onDismissWasCalled = false

        var loginSaveRequest: PromptRequest.SaveLoginPrompt = mock()

        val promptDelegate = spy(GeckoPromptDelegate(mockSession))

        mockSession.register(object : EngineSession.Observer {
            override fun onPromptRequest(promptRequest: PromptRequest) {
                loginSaveRequest = promptRequest as PromptRequest.SaveLoginPrompt
            }
        })

        val login = createLogin()
        val saveOption = Autocomplete.LoginSaveOption(login.toLoginEntry())

        var geckoResult =
            promptDelegate.onLoginSave(mock(), GeckoLoginSavePrompt(arrayOf(saveOption)))

        geckoResult!!.accept {
            onDismissWasCalled = true
        }

        loginSaveRequest.onDismiss()
        assertTrue(onDismissWasCalled)

        geckoResult = promptDelegate.onLoginSave(mock(), GeckoLoginSavePrompt(arrayOf(saveOption)))

        geckoResult!!.accept {
            onLoginSaved = true
        }

        loginSaveRequest.onConfirm(login)
        assertTrue(onLoginSaved)

        onLoginSaved = false

        loginSaveRequest.onConfirm(login)

        assertFalse(onLoginSaved)
    }

    @Test
    fun `Calling onLoginSelect must provide an SelectLoginPrompt PromptRequest`() {
        val mockSession = GeckoEngineSession(runtime)
        var onLoginSelected = false
        var onDismissWasCalled = false

        var loginSelectRequest: PromptRequest.SelectLoginPrompt = mock()

        val promptDelegate = spy(GeckoPromptDelegate(mockSession))

        mockSession.register(object : EngineSession.Observer {
            override fun onPromptRequest(promptRequest: PromptRequest) {
                loginSelectRequest = promptRequest as PromptRequest.SelectLoginPrompt
            }
        })

        val login = createLogin()
        val loginSelectOption = Autocomplete.LoginSelectOption(login.toLoginEntry())

        val secondLogin = createLogin(username = "username2")
        val secondLoginSelectOption = Autocomplete.LoginSelectOption(secondLogin.toLoginEntry())

        var geckoResult =
            promptDelegate.onLoginSelect(
                mock(),
                GeckoLoginSelectPrompt(arrayOf(loginSelectOption, secondLoginSelectOption))
            )

        geckoResult!!.accept {
            onDismissWasCalled = true
        }

        loginSelectRequest.onDismiss()
        assertTrue(onDismissWasCalled)

        geckoResult = promptDelegate.onLoginSelect(
            mock(),
            GeckoLoginSelectPrompt(arrayOf(loginSelectOption, secondLoginSelectOption))
        )

        geckoResult!!.accept {
            onLoginSelected = true
        }

        loginSelectRequest.onConfirm(login)
        assertTrue(onLoginSelected)

        onLoginSelected = false
        loginSelectRequest.onConfirm(login)

        assertFalse(onLoginSelected)
    }

    fun createLogin(
        guid: String = "id",
        password: String = "password",
        username: String = "username",
        origin: String = "https://www.origin.com",
        httpRealm: String = "httpRealm",
        formActionOrigin: String = "https://www.origin.com",
        usernameField: String = "usernameField",
        passwordField: String = "passwordField"
    ) = Login(
        guid = guid,
        origin = origin,
        password = password,
        username = username,
        httpRealm = httpRealm,
        formActionOrigin = formActionOrigin,
        usernameField = usernameField,
        passwordField = passwordField
    )

    /**
     * Converts an Android Components [Login] to a GeckoView [LoginStorage.LoginEntry]
     */
    private fun Login.toLoginEntry() = Autocomplete.LoginEntry.Builder()
        .guid(guid)
        .origin(origin)
        .formActionOrigin(formActionOrigin)
        .httpRealm(httpRealm)
        .username(username)
        .password(password)
        .build()

    @Test
    fun `Calling onAuthPrompt must provide an Authentication PromptRequest`() {
        val mockSession = GeckoEngineSession(runtime)
        var authRequest: PromptRequest.Authentication = mock()
        var onConfirmWasCalled = false
        var onConfirmOnlyPasswordWasCalled = false
        var onDismissWasCalled = false

        val promptDelegate = GeckoPromptDelegate(mockSession)
        mockSession.register(object : EngineSession.Observer {
            override fun onPromptRequest(promptRequest: PromptRequest) {
                authRequest = promptRequest as PromptRequest.Authentication
            }
        })

        var geckoResult =
            promptDelegate.onAuthPrompt(mock(), GeckoAuthPrompt(authOptions = mock()))
        geckoResult!!.accept {
            onConfirmWasCalled = true
        }

        authRequest.onConfirm("", "")
        assertTrue(onConfirmWasCalled)

        onConfirmWasCalled = false
        authRequest.onConfirm("", "")

        assertFalse(onConfirmWasCalled)

        geckoResult =
            promptDelegate.onAuthPrompt(mock(), GeckoAuthPrompt(authOptions = mock()))
        geckoResult!!.accept {
            onDismissWasCalled = true
        }

        authRequest.onDismiss()
        assertTrue(onDismissWasCalled)

        val authOptions = GeckoAuthOptions()
        ReflectionUtils.setField(authOptions, "level", GECKO_AUTH_LEVEL.SECURE)

        var flags = 0
        flags = flags.or(GECKO_AUTH_FLAGS.ONLY_PASSWORD)
        flags = flags.or(GECKO_AUTH_FLAGS.PREVIOUS_FAILED)
        flags = flags.or(GECKO_AUTH_FLAGS.CROSS_ORIGIN_SUB_RESOURCE)
        flags = flags.or(GECKO_AUTH_FLAGS.HOST)
        ReflectionUtils.setField(authOptions, "flags", flags)

        val geckoPrompt = GeckoAuthPrompt(authOptions = authOptions)
        geckoResult = promptDelegate.onAuthPrompt(mock(), geckoPrompt)
        geckoResult!!.accept {
            val hasPassword = geckoPrompt.getGeckoResult().containsKey("password")
            val hasUser = geckoPrompt.getGeckoResult().containsKey("username")
            onConfirmOnlyPasswordWasCalled = hasPassword && hasUser == false
        }

        authRequest.onConfirm("", "")

        with(authRequest) {
            assertTrue(onlyShowPassword)
            assertTrue(previousFailed)
            assertTrue(isCrossOrigin)

            assertEquals(method, AC_AUTH_METHOD.HOST)
            assertEquals(level, AC_AUTH_LEVEL.SECURED)
            assertTrue(onConfirmOnlyPasswordWasCalled)
        }

        ReflectionUtils.setField(authOptions, "level", GECKO_AUTH_LEVEL.PW_ENCRYPTED)

        promptDelegate.onAuthPrompt(mock(), GeckoAuthPrompt(authOptions = authOptions))

        assertEquals(authRequest.level, AC_AUTH_LEVEL.PASSWORD_ENCRYPTED)

        ReflectionUtils.setField(authOptions, "level", -2423)

        promptDelegate.onAuthPrompt(mock(), GeckoAuthPrompt(authOptions = authOptions))

        assertEquals(authRequest.level, AC_AUTH_LEVEL.NONE)
    }

    @Test
    fun `Calling onColorPrompt must provide a Color PromptRequest`() {
        val mockSession = GeckoEngineSession(runtime)
        var colorRequest: PromptRequest.Color = mock()
        var onConfirmWasCalled = false
        var onDismissWasCalled = false

        val promptDelegate = GeckoPromptDelegate(mockSession)
        mockSession.register(object : EngineSession.Observer {
            override fun onPromptRequest(promptRequest: PromptRequest) {
                colorRequest = promptRequest as PromptRequest.Color
            }
        })

        var geckoResult =
            promptDelegate.onColorPrompt(mock(), GeckoColorPrompt(defaultValue = "#e66465"))
        geckoResult!!.accept {
            onConfirmWasCalled = true
        }

        with(colorRequest) {

            assertEquals(defaultColor, "#e66465")

            onConfirm("#f6b73c")
            assertTrue(onConfirmWasCalled)

            onConfirmWasCalled = false
            onConfirm("#f6b73c")

            assertFalse(onConfirmWasCalled)
        }

        geckoResult = promptDelegate.onColorPrompt(mock(), GeckoColorPrompt())
        geckoResult!!.accept {
            onDismissWasCalled = true
        }

        colorRequest.onDismiss()
        assertTrue(onDismissWasCalled)

        with(colorRequest) {
            assertEquals(defaultColor, "defaultValue")
        }
    }

    @Test
    fun `onTextPrompt must provide an TextPrompt PromptRequest`() {
        val mockSession = GeckoEngineSession(runtime)
        var request: PromptRequest.TextPrompt = mock()
        var dismissWasCalled = false
        var confirmWasCalled = false

        val promptDelegate = GeckoPromptDelegate(mockSession)

        mockSession.register(object : EngineSession.Observer {
            override fun onPromptRequest(promptRequest: PromptRequest) {
                request = promptRequest as PromptRequest.TextPrompt
            }
        })

        var geckoResult = promptDelegate.onTextPrompt(mock(), GeckoTextPrompt())
        geckoResult!!.accept {
            dismissWasCalled = true
        }

        with(request) {
            assertEquals(title, "title")
            assertEquals(inputLabel, "message")
            assertEquals(inputValue, "defaultValue")

            onDismiss()
            assertTrue(dismissWasCalled)
        }

        geckoResult = promptDelegate.onTextPrompt(mock(), GeckoTextPrompt())
        geckoResult!!.accept {
            confirmWasCalled = true
        }

        request.onConfirm(true, "newInput")
        assertTrue(confirmWasCalled)

        confirmWasCalled = false
        request.onConfirm(true, "newInput")

        assertFalse(confirmWasCalled)
    }

    @Test
    fun `onPopupRequest must provide a Popup PromptRequest`() {
        val mockSession = GeckoEngineSession(runtime)
        var request: PromptRequest.Popup? = null
        var onAllowWasCalled = false
        var onDenyWasCalled = false

        val promptDelegate = GeckoPromptDelegate(mockSession)

        mockSession.register(object : EngineSession.Observer {
            override fun onPromptRequest(promptRequest: PromptRequest) {
                request = promptRequest as PromptRequest.Popup
            }
        })

        var geckoPrompt = GeckoPopupPrompt(targetUri = "www.popuptest.com/")
        var geckoResult = promptDelegate.onPopupPrompt(mock(), geckoPrompt)
        geckoResult.accept {
            onAllowWasCalled = geckoPrompt.getGeckoResult()["response"] == true
        }

        with(request!!) {
            assertEquals(targetUri, "www.popuptest.com/")

            onAllow()
            assertTrue(onAllowWasCalled)

            onAllowWasCalled = false
            onAllow()

            assertFalse(onAllowWasCalled)
        }

        geckoPrompt = GeckoPopupPrompt()
        geckoResult = promptDelegate.onPopupPrompt(mock(), geckoPrompt)
        geckoResult.accept {
            onDenyWasCalled = geckoPrompt.getGeckoResult()["response"] == false
        }

        request!!.onDeny()
        assertTrue(onDenyWasCalled)

        onDenyWasCalled = false
        request!!.onDeny()

        assertFalse(onDenyWasCalled)
    }

    @Test
    fun `onBeforeUnloadPrompt must provide a BeforeUnload PromptRequest`() {
        val mockSession = GeckoEngineSession(runtime)
        var request: PromptRequest.BeforeUnload? = null
        var onAllowWasCalled = false
        var onDenyWasCalled = false

        val promptDelegate = GeckoPromptDelegate(mockSession)

        mockSession.register(object : EngineSession.Observer {
            override fun onPromptRequest(promptRequest: PromptRequest) {
                request = promptRequest as PromptRequest.BeforeUnload
            }
        })

        var geckoPrompt = GeckoBeforeUnloadPrompt()
        var geckoResult = promptDelegate.onBeforeUnloadPrompt(mock(), geckoPrompt)

        geckoResult!!.accept {
            onAllowWasCalled = geckoPrompt.getGeckoResult()["allow"] == true
        }

        with(request!!) {
            assertEquals(title, "")

            onLeave()
            assertTrue(onAllowWasCalled)

            onAllowWasCalled = false
            onLeave()

            assertFalse(onAllowWasCalled)
        }

        geckoPrompt = GeckoBeforeUnloadPrompt()
        geckoResult = promptDelegate.onBeforeUnloadPrompt(mock(), geckoPrompt)
        geckoResult!!.accept {
            onDenyWasCalled = geckoPrompt.getGeckoResult()["allow"] == false
        }

        request!!.onStay()
        assertTrue(onDenyWasCalled)

        onDenyWasCalled = false
        request!!.onStay()

        assertFalse(onDenyWasCalled)
    }

    @Test
    fun `onBeforeUnloadPrompt will inform listeners when if navigation is cancelled`() {
        val mockSession = GeckoEngineSession(runtime)
        var onBeforeUnloadPromptCancelledCalled = false
        var request: PromptRequest.BeforeUnload = mock()

        mockSession.register(object : EngineSession.Observer {
            override fun onPromptRequest(promptRequest: PromptRequest) {
                request = promptRequest as PromptRequest.BeforeUnload
            }

            override fun onBeforeUnloadPromptDenied() {
                onBeforeUnloadPromptCancelledCalled = true
            }
        })
        val prompt = mock<GeckoBeforeUnloadPrompt>()
        doReturn(false).`when`(prompt).isComplete

        GeckoPromptDelegate(mockSession).onBeforeUnloadPrompt(mock(), prompt)
        request.onStay()

        assertTrue(onBeforeUnloadPromptCancelledCalled)
    }

    @Test
    fun `onSharePrompt must provide a Share PromptRequest`() {
        val mockSession = GeckoEngineSession(runtime)
        var request: PromptRequest.Share? = null
        var onSuccessWasCalled = false
        var onFailureWasCalled = false
        var onDismissWasCalled = false

        val promptDelegate = GeckoPromptDelegate(mockSession)

        mockSession.register(object : EngineSession.Observer {
            override fun onPromptRequest(promptRequest: PromptRequest) {
                request = promptRequest as PromptRequest.Share
            }
        })

        var geckoPrompt = GeckoSharePrompt()
        var geckoResult = promptDelegate.onSharePrompt(mock(), geckoPrompt)
        geckoResult.accept {
            onSuccessWasCalled = true
        }

        with(request!!) {
            assertEquals(data.title, "title")
            assertEquals(data.text, "text")
            assertEquals(data.url, "https://example.com")

            onSuccess()
            assertTrue(onSuccessWasCalled)

            onSuccessWasCalled = false
            onSuccess()

            assertFalse(onSuccessWasCalled)
        }

        geckoPrompt = GeckoSharePrompt()
        geckoResult = promptDelegate.onSharePrompt(mock(), geckoPrompt)
        geckoResult.accept {
            onFailureWasCalled = true
        }

        request!!.onFailure()
        assertTrue(onFailureWasCalled)

        onFailureWasCalled = false
        request!!.onFailure()

        assertFalse(onFailureWasCalled)

        geckoPrompt = GeckoSharePrompt()
        geckoResult = promptDelegate.onSharePrompt(mock(), geckoPrompt)
        geckoResult.accept {
            onDismissWasCalled = true
        }

        request!!.onDismiss()
        assertTrue(onDismissWasCalled)
    }

    @Test
    fun `onButtonPrompt must provide a Confirm PromptRequest`() {
        val mockSession = GeckoEngineSession(runtime)
        var request: PromptRequest.Confirm = mock()
        var onPositiveButtonWasCalled = false
        var onNegativeButtonWasCalled = false
        var onNeutralButtonWasCalled = false
        var dismissWasCalled = false

        val promptDelegate = GeckoPromptDelegate(mockSession)

        mockSession.register(object : EngineSession.Observer {
            override fun onPromptRequest(promptRequest: PromptRequest) {
                request = promptRequest as PromptRequest.Confirm
            }
        })

        var geckoResult = promptDelegate.onButtonPrompt(mock(), GeckoPromptPrompt())
        geckoResult!!.accept {
            onPositiveButtonWasCalled = true
        }

        with(request) {

            assertNotNull(request)
            assertEquals(title, "title")
            assertEquals(message, "message")

            onConfirmPositiveButton(false)
            assertTrue(onPositiveButtonWasCalled)

            onPositiveButtonWasCalled = false
            onConfirmPositiveButton(false)

            assertFalse(onPositiveButtonWasCalled)
        }

        geckoResult = promptDelegate.onButtonPrompt(mock(), GeckoPromptPrompt())
        geckoResult!!.accept {
            onNeutralButtonWasCalled = true
        }

        request.onConfirmNeutralButton(false)
        assertTrue(onNeutralButtonWasCalled)

        geckoResult = promptDelegate.onButtonPrompt(mock(), GeckoPromptPrompt())
        geckoResult!!.accept {
            onNegativeButtonWasCalled = true
        }

        request.onConfirmNegativeButton(false)
        assertTrue(onNegativeButtonWasCalled)

        onNegativeButtonWasCalled = false
        request.onConfirmNegativeButton(false)

        assertFalse(onNegativeButtonWasCalled)

        geckoResult = promptDelegate.onButtonPrompt(mock(), GeckoPromptPrompt())
        geckoResult!!.accept {
            dismissWasCalled = true
        }

        request.onDismiss()
        assertTrue(dismissWasCalled)
    }

    @Test
    fun `onRepostConfirmPrompt must provide a Repost PromptRequest`() {
        val mockSession = GeckoEngineSession(runtime)
        var request: PromptRequest.Repost = mock()
        var onPositiveButtonWasCalled = false
        var onNegativeButtonWasCalled = false

        mockSession.register(object : EngineSession.Observer {
            override fun onPromptRequest(promptRequest: PromptRequest) {
                request = promptRequest as PromptRequest.Repost
            }
        })

        val promptDelegate = GeckoPromptDelegate(mockSession)

        var geckoResult = promptDelegate.onRepostConfirmPrompt(mock(), GeckoRepostPrompt())
        geckoResult!!.accept {
            onPositiveButtonWasCalled = true
        }
        request.onConfirm()
        assertTrue(onPositiveButtonWasCalled)

        onPositiveButtonWasCalled = false
        request.onConfirm()

        assertFalse(onPositiveButtonWasCalled)

        geckoResult = promptDelegate.onRepostConfirmPrompt(mock(), GeckoRepostPrompt())
        geckoResult!!.accept {
            onNegativeButtonWasCalled = true
        }
        request.onDismiss()
        assertTrue(onNegativeButtonWasCalled)

        onNegativeButtonWasCalled = false
        request.onDismiss()

        assertFalse(onNegativeButtonWasCalled)
    }

    @Test
    fun `onRepostConfirmPrompt will not be able to complete multiple times`() {
        val mockSession = GeckoEngineSession(runtime)
        var request: PromptRequest.Repost = mock()

        mockSession.register(object : EngineSession.Observer {
            override fun onPromptRequest(promptRequest: PromptRequest) {
                request = promptRequest as PromptRequest.Repost
            }
        })

        val promptDelegate = GeckoPromptDelegate(mockSession)

        var prompt = mock<GeckoRepostPrompt>()
        promptDelegate.onRepostConfirmPrompt(mock(), prompt)
        doReturn(false).`when`(prompt).isComplete
        request.onConfirm()
        verify(prompt).confirm(any())

        prompt = mock()
        promptDelegate.onRepostConfirmPrompt(mock(), prompt)
        doReturn(true).`when`(prompt).isComplete
        request.onConfirm()
        verify(prompt, never()).confirm(any())

        prompt = mock()
        promptDelegate.onRepostConfirmPrompt(mock(), prompt)
        doReturn(false).`when`(prompt).isComplete
        request.onDismiss()
        verify(prompt).confirm(any())

        prompt = mock()
        promptDelegate.onRepostConfirmPrompt(mock(), prompt)
        doReturn(true).`when`(prompt).isComplete
        request.onDismiss()
        verify(prompt, never()).confirm(any())
    }

    @Test
    fun `onRepostConfirmPrompt will inform listeners when it is being dismissed`() {
        val mockSession = GeckoEngineSession(runtime)
        var onRepostPromptCancelledCalled = false
        var request: PromptRequest.Repost = mock()

        mockSession.register(object : EngineSession.Observer {
            override fun onPromptRequest(promptRequest: PromptRequest) {
                request = promptRequest as PromptRequest.Repost
            }

            override fun onRepostPromptCancelled() {
                onRepostPromptCancelledCalled = true
            }
        })
        val prompt = mock<GeckoRepostPrompt>()
        doReturn(false).`when`(prompt).isComplete

        GeckoPromptDelegate(mockSession).onRepostConfirmPrompt(mock(), prompt)
        request.onDismiss()

        assertTrue(onRepostPromptCancelledCalled)
    }

    @Test
    fun `dismissSafely only dismiss if the prompt is NOT already dismissed`() {
        val prompt = spy(GeckoAlertPrompt())
        val geckoResult = mock<GeckoResult<GeckoSession.PromptDelegate.PromptResponse>>()

        doReturn(false).`when`(prompt).isComplete

        prompt.dismissSafely(geckoResult)

        verify(geckoResult).complete(any())
    }

    @Test
    fun `dismissSafely do nothing if the prompt is already dismissed`() {
        val prompt = spy(GeckoAlertPrompt())
        val geckoResult = mock<GeckoResult<GeckoSession.PromptDelegate.PromptResponse>>()

        doReturn(true).`when`(prompt).isComplete

        prompt.dismissSafely(geckoResult)

        verify(geckoResult, never()).complete(any())
    }

    class GeckoChoicePrompt(
        title: String,
        message: String,
        type: Int,
        choices: Array<out GeckoChoice>
    ) : GeckoSession.PromptDelegate.ChoicePrompt(title, message, type, choices)

    class GeckoAlertPrompt(title: String = "title", message: String = "message") :
        GeckoSession.PromptDelegate.AlertPrompt(title, message)

    class GeckoDateTimePrompt(
        title: String = "title",
        type: Int,
        defaultValue: String = "",
        minValue: String = "",
        maxValue: String = ""
    ) : GeckoSession.PromptDelegate.DateTimePrompt(title, type, defaultValue, minValue, maxValue)

    class GeckoFilePrompt(
        title: String = "title",
        type: Int,
        capture: Int = 0,
        mimeTypes: Array<out String> = emptyArray()
    ) : GeckoSession.PromptDelegate.FilePrompt(title, type, capture, mimeTypes)

    class GeckoAuthPrompt(
        title: String = "title",
        message: String = "message",
        authOptions: AuthOptions
    ) : GeckoSession.PromptDelegate.AuthPrompt(title, message, authOptions)

    class GeckoColorPrompt(
        title: String = "title",
        defaultValue: String = "defaultValue"
    ) : GeckoSession.PromptDelegate.ColorPrompt(title, defaultValue)

    class GeckoTextPrompt(
        title: String = "title",
        message: String = "message",
        defaultValue: String = "defaultValue"
    ) : GeckoSession.PromptDelegate.TextPrompt(title, message, defaultValue)

    class GeckoPopupPrompt(
        targetUri: String = "targetUri"
    ) : GeckoSession.PromptDelegate.PopupPrompt(targetUri)

    class GeckoBeforeUnloadPrompt : GeckoSession.PromptDelegate.BeforeUnloadPrompt()

    class GeckoSharePrompt(
        title: String? = "title",
        text: String? = "text",
        url: String? = "https://example.com"
    ) : GeckoSession.PromptDelegate.SharePrompt(title, text, url)

    class GeckoPromptPrompt(
        title: String = "title",
        message: String = "message"
    ) : GeckoSession.PromptDelegate.ButtonPrompt(title, message)

    class GeckoLoginSelectPrompt(
        loginArray: Array<Autocomplete.LoginSelectOption>
    ) : GeckoSession.PromptDelegate.AutocompleteRequest<Autocomplete.LoginSelectOption>(loginArray)

    class GeckoLoginSavePrompt(
        login: Array<Autocomplete.LoginSaveOption>
    ) : GeckoSession.PromptDelegate.AutocompleteRequest<Autocomplete.LoginSaveOption>(login)

    class GeckoAuthOptions : GeckoSession.PromptDelegate.AuthPrompt.AuthOptions()

    class GeckoRepostPrompt : GeckoSession.PromptDelegate.RepostConfirmPrompt()

    private fun GeckoSession.PromptDelegate.BasePrompt.getGeckoResult(): GeckoBundle {
        val javaClass = GeckoSession.PromptDelegate.BasePrompt::class.java
        val method = javaClass.getDeclaredMethod("ensureResult")
        method.isAccessible = true
        return (method.invoke(this) as GeckoBundle)
    }
}
