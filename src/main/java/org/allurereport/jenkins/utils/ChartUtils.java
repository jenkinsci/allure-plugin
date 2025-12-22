/*
 *  Copyright 2016-2023 Qameta Software OÜ
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.allurereport.jenkins.utils;

import hudson.util.ChartUtil;
import hudson.util.ShiftedCategoryAxis;
import hudson.util.StackedAreaRenderer2;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.StackedAreaRenderer;
import org.jfree.data.category.CategoryDataset;
import org.jfree.ui.RectangleInsets;
import org.kohsuke.stapler.StaplerRequest;
import org.allurereport.jenkins.AllureReportBuildAction;
import org.allurereport.jenkins.Messages;

import java.awt.Color;

/**
 * @author Egor Borisov ehborisov@gmail.com
 */
public final class ChartUtils {

    private ChartUtils() {
    }

    @SuppressWarnings({"AnonInnerLength", "PMD.NcssCount"})
    public static JFreeChart createChart(final StaplerRequest req,
                                         final CategoryDataset dataset) {

        final String relPath = getRelPath(req);

        final JFreeChart chart = ChartFactory.createStackedAreaChart(
                "Allure history trend",
                null,
                "count",
                dataset,
                PlotOrientation.VERTICAL,
                false,
                true,
                false
        );

        chart.setBackgroundPaint(Color.white);

        final CategoryPlot plot = chart.getCategoryPlot();

        plot.setBackgroundPaint(Color.WHITE);
        plot.setOutlinePaint(null);
        plot.setForegroundAlpha(0.8f);
        plot.setRangeGridlinesVisible(true);
        plot.setRangeGridlinePaint(Color.black);

        final CategoryAxis domainAxis = new ShiftedCategoryAxis(null);
        plot.setDomainAxis(domainAxis);
        domainAxis.setCategoryLabelPositions(CategoryLabelPositions.UP_90);
        domainAxis.setLowerMargin(0.0);
        domainAxis.setUpperMargin(0.0);
        domainAxis.setCategoryMargin(0.0);

        final NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());

        final StackedAreaRenderer ar = new StackedAreaRenderer2() {
            @Override
            public String generateURL(final CategoryDataset dataset,
                                      final int row,
                                      final int column) {
                final ChartUtil.NumberOnlyBuildLabel label = (ChartUtil.NumberOnlyBuildLabel)
                        dataset.getColumnKey(column);
                return relPath + label.getRun().getNumber() + "/allure/";
            }

            @Override
            public String generateToolTip(final CategoryDataset dataset,
                                          final int row,
                                          final int column) {
                final ChartUtil.NumberOnlyBuildLabel label = (ChartUtil.NumberOnlyBuildLabel)
                        dataset.getColumnKey(column);
                final AllureReportBuildAction buildAction = label.getRun().getAction(AllureReportBuildAction.class);
                final String displayName = label.getRun().getDisplayName();
                switch (row) {
                    case 0:
                        return String.valueOf(Messages._AllureReportBuildAction_failed(displayName,
                                buildAction.getFailedCount()));
                    case 1:
                        return String.valueOf(Messages._AllureReportBuildAction_broken(displayName,
                                buildAction.getBrokenCount()));
                    case 2:
                        return String.valueOf(Messages.AllureReportBuildAction_passed(displayName,
                                buildAction.getPassedCount()));
                    case 3:
                        return String.valueOf(Messages.AllureReportBuildAction_skipped(displayName,
                                buildAction.getSkipCount()));
                    case 4:
                        return String.valueOf(Messages.AllureReportBuildAction_unknown(displayName,
                                buildAction.getUnknownCount()));
                    default:
                        return String.valueOf(Messages.AllureReportBuildAction_total(displayName,
                                buildAction.getTotalCount()));
                }
            }
        };

        ar.setSeriesPaint(0, new Color(0xfd5a3e)); // Failures.
        ar.setSeriesPaint(1, new Color(0xffd050)); //Broken
        ar.setSeriesPaint(2, new Color(0x97cc64)); //Passed
        ar.setSeriesPaint(3, new Color(0xaaaaaa)); //Skipped
        ar.setSeriesPaint(4, new Color(0xd35ebe)); //Unknown
        ar.setSeriesPaint(5, new Color(0x729fcf)); // Total.

        plot.setInsets(new RectangleInsets(0, 0, 0, 5.0));
        plot.setRenderer(ar);
        return chart;
    }

    private static String getRelPath(final StaplerRequest req) {
        final String relPath = req.getParameter("rel");
        if (relPath == null) {
            return "";
        }
        return relPath;
    }
}
