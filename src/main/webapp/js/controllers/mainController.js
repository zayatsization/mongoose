define([
	"jquery",
	"./websockets/webSocketController",
	"./tab/scenariosController",
	"./tab/defaultsController",
	"./tab/testsController",
	"text!../../templates/navbar.hbs",
	"text!../../templates/base.hbs",
	"text!../../templates/tab/buttons.hbs",
	"../util/handlebarsUtil",
	"../util/templatesUtil",
	"../util/cssUtil"
], function ($,
             webSocketController,
             scenariosController,
             defaultsController,
             testsController,
             navbarTemplate,
             baseTemplate,
             buttonsTemplate,
             hbUtil,
             templatesUtil,
             cssUtil) {
	
	const TAB_TYPE = templatesUtil.tabTypes();
	const jqId = templatesUtil.composeJqId;
	
	var currentTabType = TAB_TYPE.SCENARIOS;
	
	// render html and bind events of basic elements and same for all tabs elements 
	function render(scenariosArray, configObject) {
		const renderer = rendererFactory();
		renderer.navbar(configObject.run.version || 'unknown');
		renderer.base();
		renderer.buttons();
		scenariosController.render(scenariosArray);
		defaultsController.render(configObject);
		testsController.render();
		makeTabActive(currentTabType);
	}
	
	function tabJqId(tabType) {
		return jqId([tabType, 'tab']);
	}
	
	function makeTabActive(tabType) {
		const TAB_CLASS = templatesUtil.tabClasses();
		const BLOCK = templatesUtil.blocks();
		$.each(TAB_TYPE, function (key, value) {
			const treeId = jqId([BLOCK.TREE, value]);
			const buttonsId = jqId([BLOCK.BUTTONS, value]);
			if (value === tabType) {
				cssUtil.show(treeId, buttonsId);
				cssUtil.addClass(TAB_CLASS.ACTIVE, tabJqId(tabType))
			} else {
				cssUtil.hide(treeId, buttonsId);
				cssUtil.removeClass(TAB_CLASS.ACTIVE, tabJqId(value));
			}
		});
		currentTabType = tabType;
	}
	
	const rendererFactory = function () {
		const binder = clickEventBinderFactory();
		function renderNavbar(runVersion) {
			hbUtil.compileAndInsertInsideBefore('body', navbarTemplate, {version: runVersion});
			binder.tab();
		}
		function renderBase() {
			hbUtil.compileAndInsertInside('#app', baseTemplate);
		}
		function renderButtons() {
			// object to array
			const BUTTON_TYPE = templatesUtil.commonButtonTypes();
			const CONFIG_TABS = $.map(TAB_TYPE, function (value) { return value; }).slice(0, 2);
			const BUTTONS = $.map(BUTTON_TYPE, function (value) { return value; });
			$.each(CONFIG_TABS, function (index, value) {
				hbUtil.compileAndInsertInsideBefore('#config', buttonsTemplate,
					{ 'buttons': BUTTONS, 'tab-type': value });
			});
			binder.openButton(BUTTON_TYPE, CONFIG_TABS);
		}
		return {
			navbar: renderNavbar,
			base: renderBase,
			buttons: renderButtons
		}
	};
	
	const clickEventBinderFactory = function () {
		function bindTabClickEvent(tabType) {
			const tabId = tabJqId(tabType);
			$(tabId).click(function () {
				makeTabActive(tabType)
			});
		}
		
		function bindTabClickEvents() {
			$.each(TAB_TYPE, function (key, value) {
				bindTabClickEvent(value);
			});
		}
		
		function buttonJqId(buttonType, tabName) {
			return jqId([buttonType, tabName]);
		}
		
		function fillTheField(tabName, BUTTON_TYPE) {
			const openInputFileId = buttonJqId(BUTTON_TYPE.OPEN_INPUT_FILE, tabName);
			const openInputTextId = buttonJqId(BUTTON_TYPE.OPEN_INPUT_TEXT, tabName);
			const openFileName = $(openInputFileId).val();
			if (openFileName) {
				$(openInputTextId).val(openFileName)
			} else {
				$(openInputTextId).val('No ' + tabName.slice(0, -1) + ' chosen')
			}
		}
		
		function passClick(tabName, BUTTON_TYPE) {
			const openInputFileElem = $(buttonJqId(BUTTON_TYPE.OPEN_INPUT_FILE, tabName));
			$(buttonJqId(BUTTON_TYPE.OPEN, tabName)).click(function () {
				openInputFileElem.trigger('click');
			});
			openInputFileElem.change(function () {
				fillTheField(tabName, BUTTON_TYPE);
			})
		}
		
		function bindOpenButtonClickEvents(BUTTON_TYPE, CONFIG_TABS) {
			$.each(CONFIG_TABS, function (index, value) {
				passClick(value, BUTTON_TYPE);
			});
		}
		
		return {
			tab: bindTabClickEvents,
			openButton: bindOpenButtonClickEvents
		}
	};
	
	return {
		render: render
	};
});