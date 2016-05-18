/**
 * 
 */
package com.ge.predix.solsvc.experience.datasource.handlers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ge.predix.solsvc.bootstrap.ams.dto.Asset;
import com.ge.predix.solsvc.bootstrap.ams.dto.AssetMeter;
import com.ge.predix.solsvc.bootstrap.ams.dto.Meter;
import com.ge.predix.solsvc.experience.datasource.datagrid.dto.AssetKpiDataGrid;
import com.ge.predix.solsvc.experience.datasource.datagrid.dto.BaseKpiDataGrid;

/**
 * Component to get Data for Asset Based on Group
 * 
 * @author 212421693
 *
 */
@Component
public class AssetDataSourceHandler extends DataSourceHandler
{

    private static Logger log = LoggerFactory.getLogger(AssetDataSourceHandler.class);

    @SuppressWarnings("nls")
    @Override
    public List<BaseKpiDataGrid> getWidgetData(String id, String start_time, String end_time, String authorization)
    {

        List<Asset> allAsset = getAssetWithKpi(id, authorization);

        // call timeseries to get the currentValue

        List<BaseKpiDataGrid> groupList = new ArrayList<BaseKpiDataGrid>();

        for (Asset asset : allAsset)
        {

            LinkedHashMap<String, AssetMeter> meters = asset.getAssetMeter();
            if ( meters != null )
            {
                for (Entry<String, AssetMeter> entry : meters.entrySet())
                {
                    entry.getKey();
                    AssetMeter assetMeter = entry.getValue();
                   
                    AssetKpiDataGrid kpiDataGrid = getAnalyticsDrivenAssetDataGrid(entry.getKey(), assetMeter,
                            authorization);

                    // check for meterExtensions and if properties are set return kpiDataGrid
                    if ( kpiDataGrid == null )
                    {
                    	log.debug("getAnalyticsDrivenAssetDataGrid not found calling time series Current Value");
                        kpiDataGrid = new AssetKpiDataGrid();
                        List<Double> dataPoint = getCurrentValue(entry.getKey(), assetMeter, authorization);
                        if ( dataPoint != null && dataPoint.size() >= 2 )
                        {
                        	kpiDataGrid.setLastMeterReading(dataPoint.get(DATAPOINT_TS).longValue());
                            kpiDataGrid.setCurrentValue(dataPoint.get(DATAPOINT_VALUE).doubleValue());                          
                            kpiDataGrid.setAlertStatus(getMeterAlertStatus(dataPoint.get(DATAPOINT_VALUE), assetMeter));
                        }
                        else
                        {
                            kpiDataGrid.setCurrentValue(new Double(0));
                            kpiDataGrid.setLastMeterReading(new Long(0));
                            kpiDataGrid.setAlertStatus(getMeterAlertStatus(new Double(0), assetMeter));
                        }

                    }

                    kpiDataGrid.setMeter(entry.getKey());
                    kpiDataGrid.setMeterUri(assetMeter.getUri());
                    kpiDataGrid.setThresholdMax(assetMeter.getOutputMaximum());
                    kpiDataGrid.setThresholdMin(assetMeter.getOutputMinimum());
                    setDeltaFromThreshold(kpiDataGrid, assetMeter);

                    if ( assetMeter.getMeterDatasource().getIsKpi() != null
                            && StringUtils.containsIgnoreCase(assetMeter.getMeterDatasource().getIsKpi().toString(), "TRUE") )
                    {
                        kpiDataGrid.setMeter_isKpi(Boolean.TRUE);
                    }
                    else
                    {
                        kpiDataGrid.setMeter_isKpi(Boolean.FALSE);
                    }
                    if ( assetMeter.getMeterDatasource().getMachineUri() != null
                            && !assetMeter.getMeterDatasource().getMachineUri().toString().isEmpty() )
                    {
                        kpiDataGrid.setMeter_isPM(Boolean.TRUE);
                    }
                    else
                    {
                        kpiDataGrid.setMeter_isPM(Boolean.FALSE);
                    }

                    // meter call
                    Meter meter = getMeter(assetMeter.getUri(), authorization);
                    if ( meter != null )
                    {
                        kpiDataGrid.setUnit(meter.getUom());
                    }

                    groupList.add(kpiDataGrid);
                }

            }

        }

        return groupList;
    }

    /**
     * Method to calculate the Threshold value
     * 
     * @param kpiDataGrid
     * @param assetMeter
     */
    private void setDeltaFromThreshold(AssetKpiDataGrid kpiDataGrid, AssetMeter assetMeter)
    {
        Double currentValue = kpiDataGrid.getCurrentValue();
        Double thresholdMin = assetMeter.getOutputMinimum() != null ? assetMeter.getOutputMinimum() : new Double(0);
        Double thresholdMax = assetMeter.getOutputMaximum() != null ? assetMeter.getOutputMaximum() : new Double(0);

        log.debug("currentValue = " + currentValue + " thresholdMin = " + thresholdMin + " thresholdMax = "   //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$
                + thresholdMax);

        if ( currentValue.compareTo(thresholdMin) >= 0 && currentValue.compareTo(thresholdMax) <= 0 )
        {
            Double midRange = (thresholdMax - thresholdMin) / 2;
            if ( currentValue.compareTo(midRange) >= 0 )
            {
                Double value = ((thresholdMax - currentValue) / (thresholdMax - thresholdMin)) * 100;
                kpiDataGrid.setDeltaThresholdColor("GREEN"); //$NON-NLS-1$
                kpiDataGrid.setDeltaThresholdLevel("HIGH"); //$NON-NLS-1$
                kpiDataGrid.setDeltaThreshold(value);
                log.debug("In range ^" + value); //$NON-NLS-1$
            }
            else
            {
                Double value = ((currentValue - thresholdMin) / (thresholdMax - thresholdMin)) * 100;
                kpiDataGrid.setDeltaThresholdColor("GREEN"); //$NON-NLS-1$
                kpiDataGrid.setDeltaThresholdLevel("LOW"); //$NON-NLS-1$
                kpiDataGrid.setDeltaThreshold(value);
                log.debug("In range v" + value); //$NON-NLS-1$
            }

        }
        else if ( currentValue.compareTo(thresholdMin) < 0 )
        {
            Double value = ((currentValue - thresholdMin) / thresholdMin) * 100;
            kpiDataGrid.setDeltaThresholdColor("RED"); //$NON-NLS-1$
            kpiDataGrid.setDeltaThresholdLevel("LOW"); //$NON-NLS-1$
            kpiDataGrid.setDeltaThreshold(value);
            log.debug("RED range v" + value); //$NON-NLS-1$

        }
        else if ( currentValue.compareTo(thresholdMax) > 0 )
        {
            Double value = ((thresholdMax - currentValue) / thresholdMax) * 100;
            kpiDataGrid.setDeltaThresholdColor("RED"); //$NON-NLS-1$
            kpiDataGrid.setDeltaThresholdLevel("HIGH"); //$NON-NLS-1$
            kpiDataGrid.setDeltaThreshold(value);
            log.debug("RED range ^ " + value); //$NON-NLS-1$
        }

    }

    /***
     * 
     * @param id
     * @param authorization
     * @return
     */
    private List<Asset> getAssetWithKpi(String id, String authorization)
    {
        List<Asset> allAsset = new ArrayList<Asset>();
        Asset assets = getSummaryAsset(id, authorization);
        if ( assets != null ) allAsset.add(assets);
        return allAsset;
    }


    @Override
    public Asset getSummaryAsset(String id, String authorization)
    {
    	log.debug("Calling Summary Asset with id "+id); //$NON-NLS-1$
        return getAsset(id, authorization);
    }

}
