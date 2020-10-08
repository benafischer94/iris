// segments.rs
//
// Copyright (C) 2019-2020  Minnesota Department of Transportation
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
use crate::geo::{WebMercatorPos, Wgs84Pos};
use pointy::Pt64;
use postgres::Row;
use std::cmp::Ordering;
use std::collections::HashMap;
use std::fmt;
use std::io::Write;
use std::sync::mpsc::Receiver;

/// Road definition
#[derive(Debug)]
pub struct Road {
    name: String,
    abbrev: String,
    r_class: i16,
    direction: i16,
    scale: f32,
}

/// Geographic location
#[derive(Debug)]
pub struct GeoLoc {
    roadway: Option<String>,
    road_dir: Option<i16>,
    cross_mod: Option<i16>,
    cross_street: Option<String>,
    cross_dir: Option<i16>,
    lankmark: Option<String>,
    lat: Option<f64>,
    lon: Option<f64>,
}

/// Roadway node
#[derive(Debug)]
pub struct RNode {
    name: String,
    loc: GeoLoc,
    node_type: i32,
    pickable: bool,
    above: bool,
    transition: i32,
    lanes: i32,
    attach_side: bool,
    shift: i32,
    active: bool,
    station_id: Option<String>,
    speed_limit: i32,
    notes: String,
}

/// Segment notification message
pub enum SegMsg {
    /// Update (or add) a Road
    UpdateRoad(Road),
    /// Update (or add) an RNode
    UpdateNode(RNode),
    /// Remove an RNode
    RemoveNode(String),
    /// Enable/disable ordering for all RNodes
    Order(bool),
}

/// General direction of travel
#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
enum TravelDir {
    /// Northbound
    NB,
    /// Southbound
    SB,
    /// Eastbound
    EB,
    /// Westbound
    WB,
}

impl TravelDir {
    fn from_i16(dir: i16) -> Option<Self> {
        match dir {
            1 => Some(TravelDir::NB),
            2 => Some(TravelDir::SB),
            3 => Some(TravelDir::EB),
            4 => Some(TravelDir::WB),
            _ => None,
        }
    }
}

/// Corridor ID
#[derive(Clone, Debug, Eq, Hash, PartialEq)]
struct CorridorId {
    /// Name of corridor roadway
    roadway: String,
    /// Travel direction of corridor
    travel_dir: TravelDir,
}

/// A corridor is one direction of a roadway.
///
/// Each corridor contains a Vec of nodes.
///
/// When ordered, the first node depends on the direction of travel.  For
/// example, on a northbound corridor it would be the furthest south node.
/// The other nodes are ordered based on the nearest remaining node to the
/// previous, using haversine distance.
///
/// Invalid nodes (not active or missing lat/lon) are placed at the end.
struct Corridor {
    /// Corridor ID
    cor_id: CorridorId,
    /// Road class scale
    scale: f64,
    /// Nodes ordered by corridor direction
    nodes: Vec<RNode>,
    /// Valid node count
    count: usize,
}

/// State of all segments
#[derive(Default)]
struct SegmentState {
    /// Mapping of roads
    roads: HashMap<String, Road>,
    /// Mapping of node names to corridor IDs
    node_cors: HashMap<String, CorridorId>,
    /// Mapping of corridor IDs to Corridors
    corridors: HashMap<CorridorId, Corridor>,
    /// Ordered flag
    ordered: bool,
}

impl GeoLoc {
    /// Check if the location is valid
    fn is_valid(&self) -> bool {
        self.lat.is_some() && self.lon.is_some()
    }

    /// Get the lat/lon of the location
    fn latlon(&self) -> Option<(f64, f64)> {
        match (self.lat, self.lon) {
            (Some(lat), Some(lon)) => Some((lat, lon)),
            _ => None,
        }
    }

    /// Get the position
    fn pos(&self) -> Option<Wgs84Pos> {
        self.latlon().map(|(lat, lon)| Wgs84Pos::new(lat, lon))
    }
}

impl fmt::Display for CorridorId {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(f, "{} {:?}", self.roadway, self.travel_dir)
    }
}

impl RNode {
    /// SQL query for all RNodes
    pub const SQL_ALL: &'static str =
        "SELECT n.name, roadway, road_dir, cross_mod, cross_street, cross_dir, \
                landmark, lat, lon, node_type, pickable, above, transition, \
                lanes, attach_side, shift, active, station_id, speed_limit, \
                notes \
        FROM iris.r_node n \
        JOIN iris.geo_loc g ON n.geo_loc = g.name";

    /// SQL query for one RNode
    pub const SQL_ONE: &'static str =
        "SELECT n.name, roadway, road_dir, cross_mod, cross_street, cross_dir, \
                landmark, lat, lon, node_type, pickable, above, transition, \
                lanes, attach_side, shift, active, station_id, speed_limit, \
                notes \
        FROM iris.r_node n \
        JOIN iris.geo_loc g ON n.geo_loc = g.name \
        WHERE n.name = $1";

    /// Create an RNode from a result Row
    pub fn from_row(row: &Row) -> Self {
        let loc = GeoLoc {
            roadway: row.get(1),
            road_dir: row.get(2),
            cross_mod: row.get(3),
            cross_street: row.get(4),
            cross_dir: row.get(5),
            lankmark: row.get(6),
            lat: row.get(7),
            lon: row.get(8),
        };
        RNode {
            name: row.get(0),
            loc,
            node_type: row.get(9),
            pickable: row.get(10),
            above: row.get(11),
            transition: row.get(12),
            lanes: row.get(13),
            attach_side: row.get(14),
            shift: row.get(15),
            active: row.get(16),
            station_id: row.get(17),
            speed_limit: row.get(18),
            notes: row.get(19),
        }
    }

    /// Get the corridor ID
    fn cor_id(&self) -> Option<CorridorId> {
        match (&self.loc.roadway, self.loc.road_dir) {
            (Some(roadway), Some(road_dir)) => {
                let roadway = roadway.clone();
                match TravelDir::from_i16(road_dir) {
                    Some(travel_dir) => Some(CorridorId {
                        roadway,
                        travel_dir,
                    }),
                    None => None,
                }
            }
            _ => None,
        }
    }

    /// Check if active and the location is valid
    fn is_valid(&self) -> bool {
        self.active && self.loc.is_valid()
    }

    /// Get the lat/lon of the node
    fn latlon(&self) -> Option<(f64, f64)> {
        if self.active {
            self.loc.latlon()
        } else {
            None
        }
    }

    /// Get the node position
    fn pos(&self) -> Option<Wgs84Pos> {
        if self.active {
            self.loc.pos()
        } else {
            None
        }
    }

    /// Check if node should break segment
    fn is_break(&self) -> bool {
        self.station_id.is_some() || self.is_common()
    }

    /// Check if node is a common section
    fn is_common(&self) -> bool {
        self.transition == 6 // COMMON
    }
}

impl Road {
    /// SQL query for all Roads
    pub const SQL_ALL: &'static str =
        "SELECT name, abbrev, r_class, direction, scale \
        FROM iris.road \
        JOIN iris.road_class ON r_class = id";

    /// SQL query for one Road
    pub const SQL_ONE: &'static str =
        "SELECT name, abbrev, r_class, direction, scale \
        FROM iris.road \
        JOIN iris.road_class ON r_class = id \
        WHERE name = $1";

    /// Create a Road from a result Row
    pub fn from_row(row: &Row) -> Self {
        Road {
            name: row.get(0),
            abbrev: row.get(1),
            r_class: row.get(2),
            direction: row.get(3),
            scale: row.get(4),
        }
    }
}

const SQL_SEGMENTS_BEGIN: &str = &"BEGIN;
DROP TABLE IF EXISTS segments;
CREATE TABLE segments (
    sid BIGINT GENERATED ALWAYS AS IDENTITY,
    name TEXT,
    station TEXT,
    detector TEXT,
    zoom INTEGER,
    way GEOMETRY (Geometry, 3857)
);
INSERT INTO segments (name, station, zoom, way) VALUES
";

const SQL_SEGMENTS_COMMIT: &str = &"
CREATE INDEX segments_way_idx
          ON segments
       USING gist (way)
        WITH (fillfactor='100');
COMMIT;";

impl Corridor {
    /// Create a new corridor
    fn new(cor_id: CorridorId, scale: f64) -> Self {
        debug!("Corridor::new {}", &cor_id);
        let nodes = vec![];
        let count = 0;
        Corridor {
            cor_id,
            scale,
            nodes,
            count,
        }
    }

    /// Compare lat/lon positions in corridor direction
    fn cmp_dir(
        &self,
        lat: f64,
        lt: f64,
        lon: f64,
        ln: f64,
    ) -> Option<Ordering> {
        match self.cor_id.travel_dir {
            TravelDir::NB => lt.partial_cmp(&lat),
            TravelDir::SB => lat.partial_cmp(&lt),
            TravelDir::EB => ln.partial_cmp(&lon),
            TravelDir::WB => lon.partial_cmp(&ln),
        }
    }

    /// Get index of the first node in corridor direction
    fn first_node(&mut self) -> Option<usize> {
        let mut idx_lt_ln = None; // (node index, lat, lon)
        for (i, ref n) in self.nodes.iter().enumerate() {
            if let Some((lat, lon)) = n.latlon() {
                idx_lt_ln = Some(match idx_lt_ln {
                    None => (i, lat, lon),
                    Some((j, lt, ln)) => match self.cmp_dir(lat, lt, lon, ln) {
                        Some(Ordering::Greater) => (i, lat, lon),
                        _ => (j, lt, ln),
                    },
                });
            }
        }
        idx_lt_ln.map(|(i, _lt, _ln)| i)
    }

    /// Get index of the nearest node after idx
    fn nearest_node(&self, idx: usize) -> Option<usize> {
        let pos = self.nodes.get(idx)?.pos()?;
        let mut idx_dist = None; // (node index, distance)
        for (i, ref n) in self.nodes.iter().enumerate().skip(idx + 1) {
            if let Some(ref p) = n.pos() {
                let i_dist = (i, pos.distance_haversine(p));
                idx_dist = Some(match idx_dist {
                    None => i_dist,
                    Some((j, d)) => {
                        if d < i_dist.1 {
                            (j, d)
                        } else {
                            i_dist
                        }
                    }
                });
            }
        }
        idx_dist.map(|(i, _d)| i)
    }

    /// Order all nodes
    fn order_nodes(&mut self) {
        self.count = match self.first_node() {
            Some(i) => {
                if i > 0 {
                    self.nodes.swap(0, i);
                }
                let mut idx = 0;
                while idx < self.nodes.len() {
                    match self.nearest_node(idx) {
                        Some(j) => self.nodes.swap(idx + 1, j),
                        None => break,
                    }
                    idx += 1;
                }
                idx + 1
            }
            None => 0,
        };
        debug!(
            "order_nodes: {}, count: {} of {}",
            self.cor_id,
            self.count,
            self.nodes.len(),
        );
    }

    /// Create segments for all zoom levels
    fn create_segments<W: Write>(
        &self,
        writer: &mut W,
    ) -> Result<(), std::io::Error> {
        let pts = self.create_points();
        info!("{}: {} points", self.cor_id, pts.len());
        if !pts.is_empty() {
            let norms = create_norms(&pts);
            let meters = self.create_meterpoints();
            for zoom in 10..=18 {
                self.create_segments_zoom(writer, &pts, &norms, &meters, zoom)?;
            }
        }
        Ok(())
    }

    /// Create segments for one zoom level
    fn create_segments_zoom<W: Write>(
        &self,
        writer: &mut W,
        pts: &[Pt64],
        norms: &[Pt64],
        meters: &[f64],
        zoom: u32,
    ) -> Result<(), std::io::Error> {
        let o_scale = scale_zoom(self.scale * 16.0, zoom);
        let i_scale = scale_zoom(self.scale, zoom);
        let mut poly = Vec::<(Pt64, Pt64)>::with_capacity(16);
        let mut seg_meter = 0.0;
        let mut p_meter = 0.0;
        let mut station_id = None;
        let nodes = &self.nodes[..];
        for (ref node, (pt, (norm, meter))) in
            nodes.iter().zip(pts.iter().zip(norms.iter().zip(meters)))
        {
            let too_long = *meter >= seg_meter + 2500.0;
            let outer = *pt + *norm * o_scale;
            let inner = *pt + *norm * i_scale;
            if node.is_break() || too_long {
                if *meter < p_meter + 2500.0 {
                    poly.push((outer, inner));
                }
                if poly.len() > 1 {
                    self.write_segment(writer, &station_id, &poly, zoom)?;
                }
                poly.clear();
                seg_meter = *meter;
                station_id = node.station_id.clone();
            }
            if !node.is_common() {
                poly.push((outer, inner));
            }
            p_meter = *meter;
        }
        if poly.len() > 1 {
            self.write_segment(writer, &station_id, &poly, zoom)?;
        }
        Ok(())
    }

    /// Create points for corridor nodes
    fn create_points(&self) -> Vec<Pt64> {
        self.nodes
            .iter()
            .filter_map(|n| n.pos())
            .map(|p| Pt64::from(WebMercatorPos::from(p)))
            .collect()
    }

    /// Create meter-points for corridor nodes
    fn create_meterpoints(&self) -> Vec<f64> {
        let mut meters = vec![];
        let mut meter = 0.0;
        let mut ppos: Option<Wgs84Pos> = None;
        for pos in self.nodes.iter().filter_map(|n| n.pos()) {
            if let Some(ppos) = ppos {
                meter += pos.distance_haversine(&ppos);
            }
            meters.push(meter);
            ppos = Some(pos);
        }
        meters
    }

    /// Write segment to a writer
    fn write_segment<W: Write>(
        &self,
        writer: &mut W,
        station_id: &Option<String>,
        poly: &[(Pt64, Pt64)],
        zoom: u32,
    ) -> Result<(), std::io::Error> {
        write!(writer, "('{}',", self.cor_id)?;
        match station_id {
            Some(sid) => write!(writer, "'{}'", sid)?,
            None => write!(writer, "NULL")?,
        }
        write!(writer, ",{}", zoom)?;
        write!(writer, ",ST_GeomFromText('POLYGON((")?;
        let mut first = true;
        for (vtx, _) in poly {
            if first {
                first = false;
            } else {
                write!(writer, ",")?;
            }
            write!(writer, "{:.2} {:.2}", vtx.x(), vtx.y())?;
        }
        for (_, vtx) in poly.iter().rev() {
            write!(writer, ",{:.2} {:.2}", vtx.x(), vtx.y())?;
        }
        if let Some((vtx, _)) = poly.iter().next() {
            write!(writer, ",{:.2} {:.2}", vtx.x(), vtx.y())?;
        }
        writeln!(writer, "))',3857)),")?;
        Ok(())
    }

    /// Add a node to corridor
    fn add_node(&mut self, node: RNode, ordered: bool) {
        debug!(
            "add_node {} to {} ({} + 1)",
            &node.name,
            &self.cor_id,
            self.nodes.len()
        );
        let order = ordered && node.is_valid();
        self.nodes.push(node);
        if order {
            self.order_nodes();
        }
    }

    /// Update a node
    fn update_node(&mut self, node: RNode, ordered: bool) {
        debug!(
            "update_node {} to {} ({})",
            &node.name,
            &self.cor_id,
            self.nodes.len()
        );
        let valid_after = node.is_valid();
        let mut valid_before = false;
        match self.nodes.iter_mut().find(|n| n.name == node.name) {
            Some(n) => {
                valid_before = n.is_valid();
                *n = node;
            }
            None => error!("update_node: {} not found", node.name),
        }
        if ordered && (valid_before || valid_after) {
            self.order_nodes();
        }
    }

    /// Remove a node
    fn remove_node(&mut self, name: &str, ordered: bool) {
        debug!(
            "remove_node {} from {} ({} - 1)",
            &name,
            &self.cor_id,
            self.nodes.len()
        );
        match self.nodes.iter().position(|n| n.name == name) {
            Some(idx) => {
                let node = self.nodes.remove(idx);
                if ordered && node.is_valid() {
                    self.order_nodes();
                }
            }
            None => error!("remove_node: {} not found", name),
        }
    }
}

/// Create normal vectors for a slice of points
fn create_norms(pts: &[Pt64]) -> Vec<Pt64> {
    let mut norms = vec![];
    for i in 0..pts.len() {
        let upstream = vector_upstream(&pts, i);
        let downstream = vector_downstream(&pts, i);
        let v0 = match (upstream, downstream) {
            (Some(up), Some(down)) => (up + down).normalize(),
            (Some(up), None) => up,
            (None, Some(down)) => down,
            (None, None) => todo!("use corridor direction"),
        };
        norms.push(v0.left());
    }
    norms
}

/// Get vector from upstream point to current point
fn vector_upstream(pts: &[Pt64], i: usize) -> Option<Pt64> {
    let current = pts[i];
    for up in pts[0..i].iter().rev() {
        if *up != current {
            return Some((*up - current).normalize());
        }
    }
    None
}

/// Get vector from current point to downstream point
fn vector_downstream(pts: &[Pt64], i: usize) -> Option<Pt64> {
    let current = pts[i];
    for down in pts[i + 1..].iter() {
        if *down != current {
            return Some((current - *down).normalize());
        }
    }
    None
}

/// Scale a vector normal with zoom level
fn scale_zoom(scale: f64, zoom: u32) -> f64 {
    scale * f64::from(1 << (16 - 16.min(zoom)))
}

impl SegmentState {
    /// Get scale for a road
    fn scale(&self, road: &str) -> f64 {
        self.roads
            .get(road)
            .map(|r| f64::from(r.scale / 6.0))
            .unwrap_or(1.0)
    }

    /// Update (or add) a node
    fn update_node(&mut self, node: RNode) {
        match node.cor_id() {
            Some(ref cor_id) => {
                match self.node_cors.insert(node.name.clone(), cor_id.clone()) {
                    None => self.add_corridor_node(&cor_id, node),
                    Some(ref cid) if cid != cor_id => {
                        self.remove_corridor_node(cid, &node.name);
                        self.add_corridor_node(cor_id, node);
                    }
                    Some(_) => self.update_corridor_node(cor_id, node),
                }
            }
            _ => debug!("ignoring node: {}", &node.name),
        }
    }

    /// Add a node to corridor
    fn add_corridor_node(&mut self, cid: &CorridorId, node: RNode) {
        match self.corridors.get_mut(&cid) {
            Some(cor) => cor.add_node(node, self.ordered),
            None => {
                let scale = self.scale(&cid.roadway);
                let mut cor = Corridor::new(cid.clone(), scale);
                cor.add_node(node, self.ordered);
                self.corridors.insert(cid.clone(), cor);
            }
        }
    }

    /// Remove a node from a corridor
    fn remove_corridor_node(&mut self, cid: &CorridorId, name: &str) {
        match self.corridors.get_mut(cid) {
            Some(cor) => {
                cor.remove_node(name, self.ordered);
                if cor.nodes.is_empty() {
                    debug!("removing corridor: {:?}", cid);
                    self.corridors.remove(cid);
                }
            }
            None => error!("corridor ID not found {:?}", cid),
        }
    }

    /// Update a node on a corridor
    fn update_corridor_node(&mut self, cid: &CorridorId, node: RNode) {
        match self.corridors.get_mut(cid) {
            Some(cor) => cor.update_node(node, self.ordered),
            None => error!("corridor ID not found {:?}", cid),
        }
    }

    /// Remove a node
    fn remove_node(&mut self, name: &str) {
        match self.node_cors.remove(name) {
            Some(ref cid) => self.remove_corridor_node(cid, name),
            None => error!("corridor not found for node: {}", name),
        }
    }

    /// Order all corridors
    fn set_ordered(&mut self, ordered: bool) {
        self.ordered = ordered;
        if ordered {
            let mut file = std::fs::File::create("segments.sql").unwrap();
            write!(file, "{}", SQL_SEGMENTS_BEGIN).unwrap();
            for cor in self.corridors.values_mut() {
                cor.order_nodes();
                cor.create_segments(&mut file).unwrap();
            }
            writeln!(file, "{}", SQL_SEGMENTS_COMMIT).unwrap();
        }
    }

    /// Update a road class or scale
    fn update_road(&mut self, road: Road) {
        debug!("update_road {}", road.name);
        let name = road.name.clone();
        self.roads.insert(name.clone(), road);
        if self.ordered {
            let scale = self.scale(&name);
            for cor in self.corridors.values_mut() {
                if cor.cor_id.roadway == name {
                    cor.scale = scale;
                    cor.order_nodes();
                    // FIXME: create segments
                }
            }
        }
    }
}

/// Receive segment messages and update corridor segments
pub fn receive_nodes(receiver: Receiver<SegMsg>) {
    let mut state = SegmentState::default();
    loop {
        match receiver.recv().unwrap() {
            SegMsg::UpdateRoad(road) => state.update_road(road),
            SegMsg::UpdateNode(node) => state.update_node(node),
            SegMsg::RemoveNode(name) => state.remove_node(&name),
            SegMsg::Order(ordered) => state.set_ordered(ordered),
        }
        debug!("total corridors: {}", state.corridors.len());
    }
}
