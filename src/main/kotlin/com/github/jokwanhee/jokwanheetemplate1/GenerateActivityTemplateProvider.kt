package com.github.jokwanhee.jokwanheetemplate1

import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.CheckBoxWidget
import com.android.tools.idea.wizard.template.Constraint
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.PackageNameWidget
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.StringParameter
import com.android.tools.idea.wizard.template.Template
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.TextFieldWidget
import com.android.tools.idea.wizard.template.WizardTemplateProvider
import com.android.tools.idea.wizard.template.WizardUiContext
import com.android.tools.idea.wizard.template.activityToLayout
import com.android.tools.idea.wizard.template.booleanParameter
import com.android.tools.idea.wizard.template.escapeKotlinIdentifier
import com.android.tools.idea.wizard.template.impl.activities.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.defaultPackageNameParameter
import com.android.tools.idea.wizard.template.layoutToActivity
import com.android.tools.idea.wizard.template.stringParameter
import com.android.tools.idea.wizard.template.template
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class GenerateActivityTemplateProvider : WizardTemplateProvider() {
    override fun getTemplates(): List<Template> {
        return listOf(activityTemplate)
    }
}

val activityTemplate
    get() = template {
        name = "Custom Activity"
        description = "Activity 생성"
        minApi = 26

        // 우클릭시 메뉴가 표시 될 위치, New>Activity>Custom Activity 로 표시
        category = Category.Activity
        // 하드웨어 종류, Mobile, Wear, Tv, Automotive, Generic 총 5가지
        formFactor = FormFactor.Mobile
        // 표시 될 위치, 우클릭시 Activity 메뉴 아래와 Activity>Gallery 선택 시 맨 끝, 신규 프로젝트나 모듈 생성시 표시
        screens = listOf(
            WizardUiContext.ActivityGallery,
            WizardUiContext.MenuEntry,
            WizardUiContext.NewProject,
            WizardUiContext.NewModule
        )

        // 기본 패키지명을 저장
        val packageName = defaultPackageNameParameter
        lateinit var layoutName: StringParameter
        // activity 이름을 받기 위한 string parameter
        val activityName = stringParameter {
            name = "Activity 이름"
            default = "MainActivity"
            constraints = listOf(Constraint.ACTIVITY, Constraint.NONEMPTY)
            // layout 이름으로 activity 이름 제안
            suggest = { layoutToActivity(layoutName.value) }
        }
        // layout 을 구성하는 xml 파일을 만들지 체크하기 위한 boolean parameter
        val generateLayout = booleanParameter {
            name = "layout file 생성 여부"
            default = true
        }
        // layout 이름을 받을 string parameter 로 위의 boolean 값에 따라 화면 표시 여부 추가
        layoutName = stringParameter {
            name = "Layout 이름"
            visible = { generateLayout.value }
            default = "activity_main"
            constraints = listOf(Constraint.LAYOUT, Constraint.NONEMPTY, Constraint.UNIQUE)
            // activity 이름으로 layout 이름 제안
            suggest = { activityToLayout(activityName.value) }
        }
        // launcher activity 설정
        val isLauncher = booleanParameter {
            name = "Launcher Activity"
            default = false
            help = "선택하면 CATEGORY_LAUNCHER intent filter 가 추가 됩니다."
        }

        // 화면 구성
        widgets(
            TextFieldWidget(activityName),
            CheckBoxWidget(generateLayout),
            TextFieldWidget(layoutName),
            CheckBoxWidget(isLauncher),
            PackageNameWidget(packageName)
        )

        // recipe 를 사용하여 template 의 결과물 생성
        recipe = { data: TemplateData ->
            generateActivity(
                moduleData = data as ModuleTemplateData,
                packageName = packageName.value,
                activityName = activityName.value,
                generateLayout = generateLayout.value,
                isLauncher = isLauncher.value,
                layoutName = layoutName.value
            )
        }
    }


fun RecipeExecutor.generateActivity(
    moduleData: ModuleTemplateData,
    packageName: String,
    activityName: String,
    generateLayout: Boolean,
    isLauncher: Boolean,
    layoutName: String
) {
    // project 의 데이터, source code/resource/manifest 의 경로
    val (projectTemplateData, srcDir, resDir, manifestDir) = moduleData

    addAllKotlinDependencies(moduleData)

    // 날짜
    val date = LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
    // activity, layout 경로
    val activityPath = srcDir.resolve("${activityName}.kt")
    val layoutPath = resDir.resolve("layout/${layoutName}.xml")

    // AndroidManifest.xml 에 내용 병합
    mergeXml(
        generateAndroidManifest(packageName, activityName, isLauncher),
        manifestDir.resolve("AndroidManifest.xml")
    )

    // layout 생성 여부
    if (generateLayout) {
        // activity 코틀린 파일 저장
        save(
            generateActivityKt(
                date = date,
                packageName = packageName,
                activityName = activityName,
                layoutName = layoutName
            ),
            activityPath
        )
        // layout 파일 저장
        save(
            generateActivityLayout(
                packageName = packageName,
                activityName = activityName
            ),
            layoutPath
        )
    } else {
        // activity 코틀린 파일만 저장
        save(
            generateActivityKt(
                date = date,
                packageName = packageName,
                activityName = activityName
            ),
            activityPath
        )
    }

    // activity, layout 파일을 열어 화면에 표시
    open(activityPath)
    if (generateLayout) open(layoutPath)
}

fun generateAndroidManifest(
    packageName: String,
    activityName: String,
    isLauncher: Boolean
) = """
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <application>

        <activity
            android:name="${packageName}.$activityName"
            android:exported="$isLauncher"${
    if (isLauncher) {
        ">\n" +
                "            <intent-filter>\n" +
                "                <action android:name=\"android.intent.action.MAIN\" />\n" +
                "                <category android:name=\"android.intent.category.LAUNCHER\" />\n" +
                "            </intent-filter>\n" +
                "        </activity>"
    } else {
        " />"
    }
}
    
    </application>

</manifest>

"""

fun generateActivityKt(
    date: String,
    packageName: String,
    activityName: String
) = """
package ${escapeKotlinIdentifier(packageName)}

import com.example.testapplication.BaseActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope

/**
 * Created by HOI on ${date}.
 */
@AndroidEntryPoint
class $activityName : BaseActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        // unused remove
                    },
                    bottomBar = {
                        // unused remove
                    },
                    containerColor = colorResource(id = R.color.contents_bg)
                ) { contentPadding ->
                    TODO("Not yet implemented")
                }
            }
        }
    }
}


"""

fun generateActivityKt(
    date: String,
    packageName: String,
    activityName: String,
    layoutName: String
) = """
package ${escapeKotlinIdentifier(packageName)}

import com.example.testapplication.R
import com.example.testapplication.databinding.${layoutToActivity(layoutName)}Binding
import com.example.testapplication.BaseActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope

/**
 * Created by HOI on ${date}.
 */
@AndroidEntryPoint
class $activityName : BaseActivity() {

    private val binding by binding<${layoutToActivity(layoutName)}Binding>(R.layout.${layoutName})
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding.apply {
            TODO("Not yet implemented")
        }
    }
}


"""


fun generateActivityLayout(
    packageName: String,
    activityName: String
) = """
<?xml version="1.0" encoding="utf-8"?>
<layout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools">

    <data>

        <variable
            name="activity"
            type="${packageName}.${activityName}" />

    </data>

    <androidx.constraintlayout.widget.ConstraintLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="#FFFFFF"
        tools:context="${packageName}.${activityName}">

    </androidx.constraintlayout.widget.ConstraintLayout>

</layout>


"""