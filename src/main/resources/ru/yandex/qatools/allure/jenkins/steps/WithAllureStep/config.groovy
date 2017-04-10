package ru.yandex.qatools.allure.jenkins.steps.WithAllureStep

def f = namespace(lib.FormTagLib) as lib.FormTagLib

f.entry(field: 'commandline', title: _('Allure Commandline installation')) {
    f.select()
}
