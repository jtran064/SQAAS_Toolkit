package com.sqasquared.toolkit;

import com.sqasquared.toolkit.connection.DataObject;

import java.util.HashMap;

/**
 * Created by jimmytran on 11/1/16.
 */
interface TreeAlgorithmInterface<T extends DataObject> {
    DataObject constructTree(HashMap<String, T> map);
}
