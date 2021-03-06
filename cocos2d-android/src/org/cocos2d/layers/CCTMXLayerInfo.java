package org.cocos2d.layers;

import org.cocos2d.types.CGPoint;
import org.cocos2d.types.CGSize;

import java.nio.IntBuffer;
import java.util.HashMap;

/* CCTMXLayerInfo contains the information about the layers like:
 - Layer name
 - Layer size
 - Layer opacity at creation time (it can be modified at runtime)
 - Whether the layer is visible (if it's not visible, then the CocosNode won't be created)

 This information is obtained from the TMX file.
 */
public class CCTMXLayerInfo  {
	String			name;
	CGSize			layerSize;
	IntBuffer		tiles;
	boolean			visible;
	float			opacity = 1;
	boolean			ownTiles;
	int				minGID;
	int				maxGID;
	HashMap<String, String>		properties;
	CGPoint				offset;

	public CCTMXLayerInfo()	{
		super();

		ownTiles = true;
		minGID = 100000;
		maxGID = 0;
		name = null;
		tiles = null;
		offset = CGPoint.zero();
		properties = new HashMap<String, String>();
	}
}
