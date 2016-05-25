define(['jquery',
		'd3js',
		'../common/util/templatesUtil',
		'../common/constants'],
	function ($,
	          d3,
	          templatesUtil,
	          constants) {

		const plainId = templatesUtil.composeId;
		const jqId = templatesUtil.composeJqId;

		var currentChartboardContent;

		const MARGIN = {
			TOP: 20,
			RIGHT: 20,
			BOTTOM: 180,
			LEFT: 100
		};
		const WIDTH = 1200 - MARGIN.LEFT - MARGIN.RIGHT;
		const HEIGHT = 600 - MARGIN.TOP - MARGIN.BOTTOM;

		const defaultsFactory = function () {

			function createDefaultTimeScale() {
				return d3.time.scale();
			}

			function createDefaultLinearScale() {
				return d3.scale.linear();
			}

			function createDefaultAxis() {
				return d3.svg.axis();
			}

			function createDefaultLineGenerator() {
				return d3.svg.line();
			}

			function createDefaultColorizer() {
				return d3.scale.category10();
			}

			return {
				timeScale: createDefaultTimeScale,
				linearScale: createDefaultLinearScale,
				axis: createDefaultAxis,
				lineGenerator: createDefaultLineGenerator,
				colorizer: createDefaultColorizer
			}
		}();

		const AXIS_X_WIDTH = Math.round(WIDTH / 1.5);
		const AXIS_Y_WIDTH = HEIGHT;
		const scaleX = defaultsFactory.linearScale().range([0, AXIS_X_WIDTH]);
		const scaleY = defaultsFactory.linearScale().range([AXIS_Y_WIDTH, 0]);

		const axisX = defaultsFactory.axis().scale(scaleX).orient('bottom').ticks(5);
		const axisY = defaultsFactory.axis().scale(scaleY).orient('left').ticks(5);

		function xAccessor(data) {
			return data.x;
		}

		function yAccessor(data) {
			return data.y;
		}

		function scaledXAccessor(data) {
			return scaleX(xAccessor(data));
		}

		function scaledYAccessor(data) {
			return scaleY(yAccessor(data));
		}

		function extent(array, accessor) {
			return d3.extent(array.values, accessor);
		}

		function deepExtent(array, accessor) {
			return [
				d3.min(array, function (anArray) {
					return d3.min(anArray.values, accessor);
				}),
				d3.max(array, function (anArray) {
					return d3.max(anArray.values, accessor);
				})
			]
		}

		const line = defaultsFactory.lineGenerator().x(scaledXAccessor).y(scaledYAccessor);
		const colorizer = defaultsFactory.colorizer();

		function createSvg(parentSelector, svgId) {
			const svgChain = d3.select(parentSelector)
				.append('svg')
				.attr('id', svgId)
				.attr('width', WIDTH + MARGIN.LEFT + MARGIN.RIGHT)
				.attr('height', HEIGHT + MARGIN.TOP + MARGIN.BOTTOM);
			return svgChain.append('g')
				.attr('transform',
					'translate(' + (MARGIN.LEFT + 70) + ',' + (MARGIN.TOP + 70) + ')');
		}

		function createAxes(svgElement) {
			svgElement.append('g')
				.attr('class', 'x-axis axis')
				.attr('transform', 'translate(0, ' + HEIGHT + ')')
				.call(axisX);

			svgElement.append('text')
				.attr('class', 'x-axis-text axis-text')
				.attr('x', AXIS_X_WIDTH / 2)
				.attr('y', HEIGHT + MARGIN.BOTTOM / 3)
				.style('text-anchor', 'middle')
				.text('t[s]');

			svgElement.append('g')
				.attr('class', 'y-axis axis')
				.call(axisY);

			svgElement.append('text')
				.attr('class', 'y-axis-text axis-text')
				.attr('transform', 'rotate(-90)')
				.attr('y', 0 - Math.round(MARGIN.LEFT * 1.5))
				.attr('x', 0 - (AXIS_Y_WIDTH / 2))
				.attr('dy', '1em')
				.style('text-anchor', 'middle');
		}

		function createLabel(svgElement, text) {
			svgElement.append('text')
				.attr('x', (AXIS_X_WIDTH / 2))
				.attr('y', 0 - (MARGIN.TOP / 2))
				.attr('text-anchor', 'middle')
				.style('font-size', '16px')
				.style('text-decoration', 'underline')
				.text(text);
		}

		function createLegend(svgElement, chartBoardName, chartArr) {
			const legend = svgElement.selectAll('.legend').data(chartArr);
			const legendEnter = legend
				.enter()
				.append('g')
				.attr('class', 'legend')
				.attr('id', function (chart) {
					return plainId(['id', chartBoardName, chart.name]);
				})
				.on('click', function () {
					const elemented =
						document.getElementById(plainId([this.id, 'line']));
					if ($(this).css('opacity') == 1) {
						d3.select(elemented)
							.transition()
							.duration(1000)
							.style('opacity', 0);
						// .style('display', 'none');
						d3.select(this)
							.transition()
							.duration(1000)
							.style('opacity', .2);
					} else {
						d3.select(elemented)
							.style('display', 'block')
							.transition()
							.duration(1000)
							.style('opacity', 1);
						d3.select(this)
							.transition()
							.duration(1000)
							.style('opacity', 1);
					}
				});
			const legendShift = [0, 30, 60];
			legendEnter.append('circle')
				.attr('cx', AXIS_X_WIDTH + 30)
				.attr('cy', function (chart, index) {
					return legendShift[index];
				})
				.attr('r', 7);
			legendEnter.append('text')
				.attr('x', AXIS_X_WIDTH + 45)
				.attr('y', function (chart, index) {
					return legendShift[index];
				});
		}

		function handleDataObj(dataObj) {
			var values = dataObj.values;
			if (values.length > 0) {
				if (values[values.length - 1] === null) {
					values.pop();
				}
				values.forEach(function (point) {
					if (point !== null) {
						point.x = +point.x;
						point.y = +point.y;
					}
				})
			}
		}

		function createChartBoard(parentSelector, svgId, chartBoardName) {
			const svgCanvasChain = createSvg(parentSelector, svgId);
			createLabel(svgCanvasChain, chartBoardName);
			createAxes(svgCanvasChain);
		}

		function updateChartBoardContent(svgSelector, chartBoardName, chartBoardContent, currentMetricName) {
			currentChartboardContent = chartBoardContent;
			updateChartBoardView(svgSelector, currentMetricName);
		}

		function updateChartBoardView(svgSelector, metricName) {
			if (!currentChartboardContent) {
				return;
			}
			const svg = d3.select(svgSelector).transition();
			svg.select('.y-axis-text')
				.text(constants.CHART_METRICS_UNITS_FORMATTER[metricName]);
			const chartArr = currentChartboardContent[metricName];
			const names = [];
			chartArr.forEach(function (chart) {
				handleDataObj(chart);
				names.push(chart.name);
			});
			colorizer.domain(names);
			scaleX.domain(extent(chartArr[0], xAccessor));
			scaleY.domain(deepExtent(chartArr, yAccessor));
			svg.select('.x-axis')
				.call(axisX);
			svg.select('.y-axis')
				.call(axisY);
			const svgCanvas = d3.select(svgSelector + ' g');
			const chart = svgCanvas.selectAll('.chart').data(chartArr);
			const chartEnter = chart.enter().append('g')
				.attr('class', 'chart');
			chartEnter.append('path')
				.attr('class', 'line')
				.attr('d', function (chart) {
					return line(chart.values)
				})
				.style('stroke', function (chart) {
					return colorizer(chart.name);
				});
				// .transition()
				// .duration(1000)
				// .attrTween('d', function (chart) {
				// 	const interpolate = d3.scale.quantile()
				// 		.domain([0, 1])
				// 		.range(d3.range(1, chart.values.length + 1));
				// 	return function (tween) {
				// 		return line(chart.values.slice(0, interpolate(tween)));
				// 	}
				// });
			// const chartUpdate = d3.select(chart);
			const chartUpdate = chart.transition();
			chartUpdate.select('path')
				.duration(750)
				.attr('d', function (chart) {
					return line(chart.values)
				});
			// const chartUpdate = d3.transition(chart);
			// chartUpdate.select('path')
			// 	.attr('d', function (chart) {
			// 		return line(chart.values)
			// 	});
			// .transition()
			// .duration(1000)
			// .attrTween('d', function (chart) {
			// 	const interpolate = d3.scale.quantile()
			// 		.domain([0, 1])
			// 		.range(d3.range(1, chart.values.length + 1));
			// 	return function (tween) {
			// 		return line(chart.values.slice(0, interpolate(tween)));
			// 	}
			// });
			// svg.selectAll('.legend g')
			// 	.style('fill', function (chart) {
			// 		return colorizer(chart.name);
			// 	});
			// svg.selectAll('.legend text')
			// 	.text(function (chart) {
			// 		return chart.name;
			// 	});
		}

		// function drawChart(svgSelector, chartArr, loadJobName) {
		//
		// 	const SVG = d3.select(svgSelector);
		//
		// 	SVG.selectAll('*').remove();
		//
		// 	const xLabel = 't[s]';
		// 	const yLabel = 'rate[mb/s]';
		//
		// 	SVG.append('text')
		// 		.attr('x', (AXIS_X_WIDTH / 2))
		// 		.attr('y', 0 - (MARGIN.TOP / 2))
		// 		.attr('text-anchor', 'middle')
		// 		.style('font-size', '16px')
		// 		.style('text-decoration', 'underline')
		// 		.text(loadJobName);
		//
		// 	var chart;
		//
		// // 	if (Array.isArray(chartArr)) {
		// // 		const names = [];
		// // 		chartArr.forEach(function (chart) {
		// // 			handleDataObj(chart);
		// // 			names.push(chart.name);
		// // 		});
		// // 		colorizer.domain(names);
		// // 		scaleX.domain(extent(chartArr[0], xAccessor));
		// // 		scaleY.domain(deepExtent(chartArr, yAccessor));
		// // 		appendAxes(SVG, xLabel, yLabel);
		// // 		chart = SVG.selectAll('.chart')
		// // 			.data(chartArr)
		// // 			.enter().append('g')
		// // 			.attr('class', 'chart')
		// // 			.attr('id', function (chart) {
		// // 				return templatesUtil.composeId(['id', loadJobName, chart.name, 'line']);
		// // 			});
		// // 		chart.append('path')
		// // 			.attr('class', 'line')
		// // 			.attr('d', function (chart) {
		// // 				return line(chart.values)
		// // 			})
		// // 			.style('stroke', function (chart) {
		// // 				return colorizer(chart.name);
		// // 			});
		// // 		const legend = SVG.selectAll('.legend')
		// // 			.data(chartArr);
		// // 		const legendEnter = legend
		// // 			.enter()
		// // 			.append('g')
		// // 			.attr('class', 'legend')
		// // 			.attr('id', function (chart) {
		// // 				return templatesUtil.composeId(['id', loadJobName, chart.name]);
		// // 			})
		// // 			.on('click', function () {
		// // 				const elemented =
		// // 					document.getElementById(templatesUtil.composeId([this.id, 'line']));
		// // 				if ($(this).css('opacity') == 1) {
		// // 					d3.select(elemented)
		// // 						.transition()
		// // 						.duration(1000)
		// // 						.style('opacity', 0);
		// // 						// .style('display', 'none');
		// // 					d3.select(this)
		// // 						.transition()
		// // 						.duration(1000)
		// // 						.style('opacity', .2);
		// // 				} else {
		// // 					d3.select(elemented)
		// // 						.style('display', 'block')
		// // 						.transition()
		// // 						.duration(1000)
		// // 						.style('opacity', 1);
		// // 					d3.select(this)
		// // 						.transition()
		// // 						.duration(1000)
		// // 						.style('opacity', 1);
		// // 				}
		// // 			});
		// // 		const legendShift = [0, 30, 60];
		// // 		legendEnter.append('circle')
		// // 			.attr('cx', AXIS_X_WIDTH + 30)
		// // 			.attr('cy', function (chart, index) {
		// // 				return legendShift[index];
		// // 			})
		// // 			.attr('r', 7)
		// // 			.style('fill', function (chart) {
		// // 				return colorizer(chart.name);
		// // 			});
		// // 		legendEnter.append('text')
		// // 			.attr('x', AXIS_X_WIDTH + 45)
		// // 			.attr('y', function (chart, index) {
		// // 				return legendShift[index];
		// // 			})
		// // 			.text(function (chart) {
		// // 				return chart.name;
		// // 			});
		// // 	}
		// // 	// else {
		// // 	// 	handleDataObj(chartObj);
		// // 	// 	scaleX.domain(extent(chartObj, xAccessor));
		// // 	// 	scaleY.domain(extent(chartObj, yAccessor));
		// // 	// 	appendAxes(SVG, xLabel, yLabel);
		// // 	// 	chart = SVG;
		// // 	// 	chart.append('path')
		// // 	// 		.attr('class', 'line')
		// // 	// 		.attr('d', line(chartObj.values));
		// // 	// }
		// }

		return {
			// drawChart: drawChart,
			createChartBoard: createChartBoard,
			updateChartBoardContent: updateChartBoardContent,
			updateChartBoardView: updateChartBoardView
		};
	});