// Copyright (C) 2022-2024  Minnesota Department of Transportation
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
use crate::alarm::Alarm;
use crate::beacon::Beacon;
use crate::cabinetstyle::CabinetStyle;
use crate::camera::Camera;
use crate::commconfig::CommConfig;
use crate::commlink::CommLink;
use crate::controller::Controller;
use crate::detector::Detector;
use crate::dms::Dms;
use crate::error::{Error, Result};
use crate::fetch::{Action, Uri};
use crate::flowstream::FlowStream;
use crate::gatearm::GateArm;
use crate::gatearmarray::GateArmArray;
use crate::geoloc::GeoLoc;
use crate::gps::Gps;
use crate::lanemarking::LaneMarking;
use crate::lcsarray::LcsArray;
use crate::lcsindication::LcsIndication;
use crate::modem::Modem;
use crate::permission::Permission;
use crate::rampmeter::RampMeter;
use crate::role::Role;
use crate::tagreader::TagReader;
use crate::user::User;
use crate::util::{Doc, HtmlStr};
use crate::videomonitor::VideoMonitor;
use crate::weathersensor::WeatherSensor;
use resources::Res;
use serde::de::DeserializeOwned;
use serde_json::map::Map;
use serde_json::Value;
use std::fmt;
use std::iter::empty;
use wasm_bindgen::JsValue;

/// CSS class for titles
const TITLE: &str = "title";

/// CSS class for names
pub const NAME: &str = "ob_name";

/// Compact "Create" card
const CREATE_COMPACT: &str = "<span class='create'>Create 🆕</span>";

/// Close button
pub const CLOSE_BUTTON: &str = "<button id='ob_close' type='button'>X</button>";

/// Location button
pub const LOC_BUTTON: &str =
    "<button id='ob_loc' type='button'>🗺️ Location</button>";

/// Delete button
pub const DEL_BUTTON: &str =
    "<button id='ob_delete' type='button'>🗑️ Delete</button>";

/// Edit button
pub const EDIT_BUTTON: &str =
    "<button id='ob_edit' type='button'>📝 Edit</button>";

/// Save button
pub const SAVE_BUTTON: &str =
    "<button id='ob_save' type='button'>🖍️ Save</button>";

/// Resource types
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Resource {
    Alarm,
    Beacon,
    CabinetStyle,
    Camera,
    CommConfig,
    CommLink,
    Controller,
    Detector,
    Dms,
    FlowStream,
    GateArm,
    GateArmArray,
    GeoLoc,
    Gps,
    LaneMarking,
    Lcs,
    LcsArray,
    LcsIndication,
    Modem,
    Permission,
    RampMeter,
    Role,
    TagReader,
    User,
    VideoMonitor,
    WeatherSensor,
}

/// Card View
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum View {
    /// Compact Create view
    CreateCompact,
    /// Create view
    Create,
    /// Compact view
    Compact,
    /// Status view (with config flag)
    Status(bool),
    /// Edit view
    Edit,
    /// Location view
    Location,
    /// Search view
    Search,
}

/// Search term
enum Search {
    /// Empty search (matches anything)
    Empty(),
    /// Normal search
    Normal(String),
    /// Exact (multi-word) search
    Exact(String),
}

/// Ancillary card view data
pub trait AncillaryData {
    type Primary;

    /// Get URI iterator
    fn uri_iter(
        &self,
        _pri: &Self::Primary,
        _view: View,
    ) -> Box<dyn Iterator<Item = Uri>> {
        Box::new(empty())
    }

    /// Set ancillary data
    fn set_data(
        &mut self,
        _pri: &Self::Primary,
        _uri: Uri,
        _data: JsValue,
    ) -> Result<bool> {
        Ok(false)
    }
}

/// A card view of a resource
pub trait Card: Default + fmt::Display + DeserializeOwned {
    type Ancillary: AncillaryData<Primary = Self> + Default;

    /// Display name
    const DNAME: &'static str;

    /// Get the resource
    fn res() -> Res;

    /// Get the list URI
    fn uri() -> Uri {
        let mut uri = Uri::from("/iris/api/");
        uri.push(Self::res().as_str());
        uri
    }

    /// Get the URI of an object
    fn uri_name(name: &str) -> Uri {
        let mut uri = Self::uri();
        uri.push(name);
        uri
    }

    /// Create from a JSON value
    fn new(json: JsValue) -> Result<Self> {
        Ok(serde_wasm_bindgen::from_value(json)?)
    }

    /// Set the name
    fn with_name(self, name: &str) -> Self;

    /// Get next suggested name
    fn next_name(_obs: &[Self]) -> String {
        "".into()
    }

    /// Get geo location name
    fn geo_loc(&self) -> Option<&str> {
        None
    }

    /// Check if a search string matches
    fn is_match(&self, _search: &str, _anc: &Self::Ancillary) -> bool {
        false
    }

    /// Convert to Create HTML
    fn to_html_create(&self, _anc: &Self::Ancillary) -> String {
        format!(
            "<div class='row'>\
              <label for='create_name'>Name</label>\
              <input id='create_name' maxlength='24' size='24' value='{self}'>\
            </div>"
        )
    }

    /// Convert to HTML view
    fn to_html(&self, view: View, _anc: &Self::Ancillary) -> String;

    /// Get changed fields from Edit form
    fn changed_fields(&self) -> String;

    /// Handle click event for a button on the card
    fn handle_click(
        &self,
        _anc: Self::Ancillary,
        _id: &str,
        _uri: Uri,
    ) -> Vec<Action> {
        Vec::new()
    }

    /// Handle input event for an element on the card
    fn handle_input(&self, _anc: Self::Ancillary, _id: &str) {
        // ignore by default
    }
}

impl TryFrom<&str> for Resource {
    type Error = ();

    fn try_from(resource_n: &str) -> std::result::Result<Self, Self::Error> {
        Self::iter()
            .find(|res| Res::from(*res).as_str() == resource_n)
            .ok_or(())
    }
}

impl From<Resource> for Res {
    fn from(res: Resource) -> Self {
        use Resource::*;
        match res {
            Alarm => Res::Alarm,
            Beacon => Res::Beacon,
            CabinetStyle => Res::CabinetStyle,
            Camera => Res::Camera,
            CommConfig => Res::CommConfig,
            CommLink => Res::CommLink,
            Controller => Res::Controller,
            Detector => Res::Detector,
            Dms => Res::Dms,
            FlowStream => Res::FlowStream,
            GateArm => Res::GateArm,
            GateArmArray => Res::GateArmArray,
            GeoLoc => Res::GeoLoc,
            Gps => Res::Gps,
            LaneMarking => Res::LaneMarking,
            Lcs => Res::Lcs,
            LcsArray => Res::LcsArray,
            LcsIndication => Res::LcsIndication,
            Modem => Res::Modem,
            Permission => Res::Permission,
            RampMeter => Res::RampMeter,
            Role => Res::Role,
            TagReader => Res::TagReader,
            User => Res::User,
            VideoMonitor => Res::VideoMonitor,
            WeatherSensor => Res::WeatherSensor,
        }
    }
}

impl fmt::Display for Resource {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(f, "{}", self.dname())
    }
}

impl Resource {
    /// Get iterator of all resource variants
    pub fn iter() -> impl Iterator<Item = Self> {
        use Resource::*;
        [
            Alarm,
            Beacon,
            CabinetStyle,
            Camera,
            CommConfig,
            CommLink,
            Controller,
            Detector,
            Dms,
            FlowStream,
            GateArm,
            GateArmArray,
            GeoLoc,
            Gps,
            LaneMarking,
            Lcs,
            LcsArray,
            LcsIndication,
            Modem,
            Permission,
            RampMeter,
            Role,
            TagReader,
            User,
            VideoMonitor,
            WeatherSensor,
        ]
        .iter()
        .cloned()
    }

    /// Get display name
    pub const fn dname(self) -> &'static str {
        match self {
            Self::Alarm => Alarm::DNAME,
            Self::Beacon => Beacon::DNAME,
            Self::CabinetStyle => CabinetStyle::DNAME,
            Self::Camera => Camera::DNAME,
            Self::CommConfig => CommConfig::DNAME,
            Self::CommLink => CommLink::DNAME,
            Self::Controller => Controller::DNAME,
            Self::Detector => Detector::DNAME,
            Self::Dms => Dms::DNAME,
            Self::FlowStream => FlowStream::DNAME,
            Self::GateArm => GateArm::DNAME,
            Self::GateArmArray => GateArmArray::DNAME,
            Self::GeoLoc => GeoLoc::DNAME,
            Self::Gps => Gps::DNAME,
            Self::LaneMarking => LaneMarking::DNAME,
            Self::Lcs => unimplemented!(),
            Self::LcsArray => LcsArray::DNAME,
            Self::LcsIndication => LcsIndication::DNAME,
            Self::Modem => Modem::DNAME,
            Self::Permission => Permission::DNAME,
            Self::RampMeter => RampMeter::DNAME,
            Self::Role => Role::DNAME,
            Self::TagReader => TagReader::DNAME,
            Self::User => User::DNAME,
            Self::VideoMonitor => VideoMonitor::DNAME,
            Self::WeatherSensor => WeatherSensor::DNAME,
        }
    }

    /// Get the URI of a resource
    fn uri(self) -> Uri {
        let mut uri = Uri::from("/iris/api/");
        uri.push(Res::from(self).as_str());
        uri
    }

    /// Get the URI of an object
    fn uri_name(self, name: &str) -> Uri {
        let mut uri = self.uri();
        uri.push(name);
        uri
    }

    /// Delete a resource by name
    pub async fn delete(self, name: &str) -> Result<()> {
        self.uri_name(name).delete().await
    }

    /// Lookup resource symbol
    pub fn symbol(self) -> &'static str {
        Res::from(self).symbol()
    }

    /// Fetch card list for a resource type
    pub async fn fetch_cards(
        self,
        search: &str,
        config: bool,
    ) -> Result<String> {
        match self {
            Self::Alarm => fetch_cards::<Alarm>(search, config).await,
            Self::Beacon => fetch_cards::<Beacon>(search, config).await,
            Self::CabinetStyle => {
                fetch_cards::<CabinetStyle>(search, config).await
            }
            Self::Camera => fetch_cards::<Camera>(search, config).await,
            Self::CommConfig => fetch_cards::<CommConfig>(search, config).await,
            Self::CommLink => fetch_cards::<CommLink>(search, config).await,
            Self::Controller => fetch_cards::<Controller>(search, config).await,
            Self::Detector => fetch_cards::<Detector>(search, config).await,
            Self::Dms => fetch_cards::<Dms>(search, config).await,
            Self::FlowStream => fetch_cards::<FlowStream>(search, config).await,
            Self::GateArm => fetch_cards::<GateArm>(search, config).await,
            Self::GateArmArray => {
                fetch_cards::<GateArmArray>(search, config).await
            }
            Self::Gps => fetch_cards::<Gps>(search, config).await,
            Self::LaneMarking => {
                fetch_cards::<LaneMarking>(search, config).await
            }
            Self::LcsArray => fetch_cards::<LcsArray>(search, config).await,
            Self::LcsIndication => {
                fetch_cards::<LcsIndication>(search, config).await
            }
            Self::Modem => fetch_cards::<Modem>(search, config).await,
            Self::Permission => fetch_cards::<Permission>(search, config).await,
            Self::RampMeter => fetch_cards::<RampMeter>(search, config).await,
            Self::Role => fetch_cards::<Role>(search, config).await,
            Self::TagReader => fetch_cards::<TagReader>(search, config).await,
            Self::User => fetch_cards::<User>(search, config).await,
            Self::VideoMonitor => {
                fetch_cards::<VideoMonitor>(search, config).await
            }
            Self::WeatherSensor => {
                fetch_cards::<WeatherSensor>(search, config).await
            }
            _ => Ok("".into()),
        }
    }

    /// Fetch a card for a given view
    pub async fn fetch_card(self, name: &str, view: View) -> Result<String> {
        match view {
            View::CreateCompact => Ok(CREATE_COMPACT.into()),
            View::Create => {
                let html = self.card_view(View::Create, name).await?;
                Ok(html_card_create(self, &html))
            }
            View::Compact => self.card_view(View::Compact, name).await,
            View::Location => match self.fetch_geo_loc(name).await? {
                Some(geo_loc) => card_location(&geo_loc).await,
                None => unreachable!(),
            },
            View::Status(config) if self.has_status() => {
                let html = self.card_view(View::Status(config), name).await?;
                Ok(self.html_card_status(name, &html))
            }
            _ => {
                let html = self.card_view(View::Edit, name).await?;
                Ok(html_card_edit(self, name, &html, DEL_BUTTON))
            }
        }
    }

    /// Fetch a card view
    async fn card_view(self, view: View, name: &str) -> Result<String> {
        match self {
            Self::Alarm => card_view::<Alarm>(self, view, name).await,
            Self::Beacon => card_view::<Beacon>(self, view, name).await,
            Self::CabinetStyle => {
                card_view::<CabinetStyle>(self, view, name).await
            }
            Self::Camera => card_view::<Camera>(self, view, name).await,
            Self::CommConfig => card_view::<CommConfig>(self, view, name).await,
            Self::CommLink => card_view::<CommLink>(self, view, name).await,
            Self::Controller => card_view::<Controller>(self, view, name).await,
            Self::Detector => card_view::<Detector>(self, view, name).await,
            Self::Dms => card_view::<Dms>(self, view, name).await,
            Self::FlowStream => card_view::<FlowStream>(self, view, name).await,
            Self::GateArm => card_view::<GateArm>(self, view, name).await,
            Self::GateArmArray => {
                card_view::<GateArmArray>(self, view, name).await
            }
            Self::GeoLoc => card_view::<GeoLoc>(self, view, name).await,
            Self::Gps => card_view::<Gps>(self, view, name).await,
            Self::LaneMarking => {
                card_view::<LaneMarking>(self, view, name).await
            }
            Self::LcsArray => card_view::<LcsArray>(self, view, name).await,
            Self::LcsIndication => {
                card_view::<LcsIndication>(self, view, name).await
            }
            Self::Modem => card_view::<Modem>(self, view, name).await,
            Self::Permission => card_view::<Permission>(self, view, name).await,
            Self::RampMeter => card_view::<RampMeter>(self, view, name).await,
            Self::Role => card_view::<Role>(self, view, name).await,
            Self::TagReader => card_view::<TagReader>(self, view, name).await,
            Self::User => card_view::<User>(self, view, name).await,
            Self::VideoMonitor => {
                card_view::<VideoMonitor>(self, view, name).await
            }
            Self::WeatherSensor => {
                card_view::<WeatherSensor>(self, view, name).await
            }
            _ => unreachable!(),
        }
    }

    /// Check if a resource has a Status view
    fn has_status(self) -> bool {
        matches!(
            self,
            Self::Alarm
                | Self::Beacon
                | Self::Camera
                | Self::CommLink
                | Self::Controller
                | Self::Detector
                | Self::Dms
                | Self::FlowStream
                | Self::GateArm
                | Self::GateArmArray
                | Self::GeoLoc
                | Self::Gps
                | Self::LaneMarking
                | Self::LcsArray
                | Self::LcsIndication
                | Self::RampMeter
                | Self::TagReader
                | Self::VideoMonitor
                | Self::WeatherSensor
        )
    }

    /// Save changed fields on card
    pub async fn save(self, name: &str) -> Result<()> {
        let changed = self.fetch_changed(name).await?;
        if !changed.is_empty() {
            self.uri_name(name).patch(&changed.into()).await?;
        }
        Ok(())
    }

    /// Fetch changed fields from an Edit view
    async fn fetch_changed(self, name: &str) -> Result<String> {
        match self {
            Self::Alarm => fetch_changed::<Alarm>(self, name).await,
            Self::Beacon => fetch_changed::<Beacon>(self, name).await,
            Self::CabinetStyle => {
                fetch_changed::<CabinetStyle>(self, name).await
            }
            Self::Camera => fetch_changed::<Camera>(self, name).await,
            Self::CommConfig => fetch_changed::<CommConfig>(self, name).await,
            Self::CommLink => fetch_changed::<CommLink>(self, name).await,
            Self::Controller => fetch_changed::<Controller>(self, name).await,
            Self::Detector => fetch_changed::<Detector>(self, name).await,
            Self::Dms => fetch_changed::<Dms>(self, name).await,
            Self::FlowStream => fetch_changed::<FlowStream>(self, name).await,
            Self::GateArm => fetch_changed::<GateArm>(self, name).await,
            Self::GateArmArray => {
                fetch_changed::<GateArmArray>(self, name).await
            }
            Self::GeoLoc => fetch_changed::<GeoLoc>(self, name).await,
            Self::Gps => fetch_changed::<Gps>(self, name).await,
            Self::LaneMarking => fetch_changed::<LaneMarking>(self, name).await,
            Self::LcsArray => fetch_changed::<LcsArray>(self, name).await,
            Self::LcsIndication => {
                fetch_changed::<LcsIndication>(self, name).await
            }
            Self::Modem => fetch_changed::<Modem>(self, name).await,
            Self::Permission => fetch_changed::<Permission>(self, name).await,
            Self::RampMeter => fetch_changed::<RampMeter>(self, name).await,
            Self::Role => fetch_changed::<Role>(self, name).await,
            Self::TagReader => fetch_changed::<TagReader>(self, name).await,
            Self::User => fetch_changed::<User>(self, name).await,
            Self::VideoMonitor => {
                fetch_changed::<VideoMonitor>(self, name).await
            }
            Self::WeatherSensor => {
                fetch_changed::<WeatherSensor>(self, name).await
            }
            _ => unreachable!(),
        }
    }

    /// Create a new object
    pub async fn create_and_post(self) -> Result<()> {
        let doc = Doc::get();
        let value = match self {
            Resource::Permission => Permission::create_value(&doc)?,
            _ => self.create_value(&doc)?,
        };
        self.uri().post(&value.into()).await?;
        Ok(())
    }

    /// Create a name value
    fn create_value(self, doc: &Doc) -> Result<String> {
        if let Some(name) = doc.input_option_string("create_name") {
            let mut obj = Map::new();
            obj.insert("name".to_string(), Value::String(name));
            return Ok(Value::Object(obj).to_string());
        }
        Err(Error::NameMissing())
    }

    /// Fetch primary JSON resource
    async fn fetch_primary<C: Card>(self, name: &str) -> Result<C> {
        let json = C::uri_name(name).get().await?;
        C::new(json)
    }

    /// Fetch geo location name (if any)
    pub async fn fetch_geo_loc(self, name: &str) -> Result<Option<String>> {
        match self {
            Self::Beacon => self.geo_loc::<Beacon>(name).await,
            Self::Camera => self.geo_loc::<Camera>(name).await,
            Self::Controller => self.geo_loc::<Controller>(name).await,
            Self::Dms => self.geo_loc::<Dms>(name).await,
            Self::GateArmArray => self.geo_loc::<GateArmArray>(name).await,
            Self::GeoLoc => Ok(Some(name.into())),
            Self::LaneMarking => self.geo_loc::<LaneMarking>(name).await,
            Self::RampMeter => self.geo_loc::<RampMeter>(name).await,
            Self::TagReader => self.geo_loc::<TagReader>(name).await,
            Self::WeatherSensor => self.geo_loc::<WeatherSensor>(name).await,
            _ => Ok(None),
        }
    }

    /// Fetch geo location name
    async fn geo_loc<C: Card>(self, name: &str) -> Result<Option<String>> {
        let pri = self.fetch_primary::<C>(name).await?;
        match pri.geo_loc() {
            Some(geo_loc) => Ok(Some(geo_loc.to_string())),
            None => Ok(None),
        }
    }

    /// Build a status card
    fn html_card_status(self, name: &str, status: &str) -> String {
        let name = HtmlStr::new(name);
        format!(
            "<div class='row'>\
              <span class='{TITLE}'>{self}</span>\
              <span class='{TITLE}'>Status</span>\
              <span class='{NAME}'>{name}</span>\
              {CLOSE_BUTTON}\
            </div>\
            {status}"
        )
    }

    /// Handle click event for a button owned by the resource
    pub async fn handle_click(self, name: &str, id: &str) -> Result<bool> {
        match self {
            Self::Beacon => handle_click::<Beacon>(self, name, id).await,
            Self::Dms => handle_click::<Dms>(self, name, id).await,
            _ => Ok(false),
        }
    }

    /// Handle input event for an element owned by the resource
    pub async fn handle_input(self, name: &str, id: &str) -> Result<bool> {
        match self {
            Self::Dms => handle_input::<Dms>(self, name, id).await,
            _ => Ok(false),
        }
    }

    /// Get all item states as html options
    pub fn item_state_options(self) -> &'static str {
        match self {
            Self::Dms => Dms::item_state_options(),
            _ => {
                "<option value=''>all ↴</option>\
                 <option value='🔹'>🔹 available</option>\
                 <option value='🔌'>🔌 offline</option>\
                 <option value='▪️'>▪️ inactive</option>"
            }
        }
    }
}

/// Fetch JSON resource array list
async fn fetch_list<C: Card>() -> Result<(Vec<C>, C::Ancillary)> {
    let json = C::uri().get().await?;
    let obs = serde_wasm_bindgen::from_value(json)?;
    // Use default value for ancillary data lookup
    let pri = C::default();
    let anc = fetch_ancillary(View::Search, &pri).await?;
    Ok((obs, anc))
}

/// Fetch card list as HTML
async fn fetch_cards<C: Card>(search: &str, config: bool) -> Result<String> {
    let (obs, anc) = fetch_list().await?;
    let rname = C::res().as_str();
    let search = Search::new(search);
    let mut html = String::new();
    html.push_str("<ul class='cards'>");
    if config {
        let next_name = C::next_name(&obs);
        // the "Create" card has id "{rname}_" and next available name
        html.push_str(&format!(
            "<li id='{rname}_' name='{next_name}' class='card'>\
                {CREATE_COMPACT}\
            </li>"
        ));
    }
    for pri in obs.iter().filter(|pri| search.is_match(*pri, &anc)) {
        html.push_str(&format!(
            "<li id='{rname}_{pri}' name='{pri}' class='card'>"
        ));
        html.push_str(&pri.to_html(View::Compact, &anc));
        html.push_str("</li>");
    }
    html.push_str("</ul>");
    Ok(html)
}

/// Fetch ancillary data
async fn fetch_ancillary<C: Card>(view: View, pri: &C) -> Result<C::Ancillary> {
    let mut anc = C::Ancillary::default();
    let mut more = true;
    while more {
        more = false;
        for uri in anc.uri_iter(pri, view) {
            match uri.get().await {
                Ok(data) => {
                    if anc.set_data(pri, uri, data)? {
                        more = true;
                    }
                }
                Err(Error::FetchResponseNotFound())
                | Err(Error::FetchResponseForbidden()) => {
                    // Ok, move on to the next one
                }
                Err(e) => return Err(e),
            }
        }
    }
    Ok(anc)
}

/// Fetch changed fields from an Edit view
async fn fetch_changed<C: Card>(res: Resource, name: &str) -> Result<String> {
    let pri = res.fetch_primary::<C>(name).await?;
    Ok(pri.changed_fields())
}

/// Handle click event for a button on a card
async fn handle_click<C: Card>(
    res: Resource,
    name: &str,
    id: &str,
) -> Result<bool> {
    let pri = res.fetch_primary::<C>(name).await?;
    let anc = fetch_ancillary(View::Status(false), &pri).await?;
    let uri = C::uri_name(name);
    for action in pri.handle_click(anc, id, uri) {
        action.perform().await?;
    }
    Ok(true)
}

/// Handle input event for an element on a card
async fn handle_input<C: Card>(
    res: Resource,
    name: &str,
    id: &str,
) -> Result<bool> {
    let pri = res.fetch_primary::<C>(name).await?;
    let anc = fetch_ancillary(View::Status(false), &pri).await?;
    pri.handle_input(anc, id);
    Ok(true)
}

/// Fetch a card view
async fn card_view<C: Card>(
    res: Resource,
    view: View,
    name: &str,
) -> Result<String> {
    let pri = if view == View::Create {
        C::default().with_name(name)
    } else {
        res.fetch_primary::<C>(name).await?
    };
    let anc = fetch_ancillary(view, &pri).await?;
    Ok(pri.to_html(view, &anc))
}

/// Fetch a Location card
async fn card_location(name: &str) -> Result<String> {
    let html = Resource::GeoLoc.card_view(View::Edit, name).await?;
    Ok(html_card_edit(Resource::GeoLoc, name, &html, ""))
}

impl Search {
    /// Create a new search term
    fn new(se: &str) -> Self {
        let se = se.to_lowercase();
        if se.is_empty() {
            Search::Empty()
        } else if se.starts_with('"') && se.ends_with('"') {
            Search::Exact(se.trim_matches('"').to_string())
        } else {
            Search::Normal(se)
        }
    }

    /// Test if a card matches the search
    fn is_match<C: Card>(&self, pri: &C, anc: &C::Ancillary) -> bool {
        match self {
            Search::Empty() => true,
            Search::Normal(se) => se.split(' ').all(|s| pri.is_match(s, anc)),
            Search::Exact(se) => pri.is_match(se, anc),
        }
    }
}

impl View {
    /// Is the view compact?
    pub fn is_compact(self) -> bool {
        matches!(self, View::Compact | View::CreateCompact)
    }

    /// Is the view a create view?
    pub fn is_create(self) -> bool {
        matches!(self, View::Create | View::CreateCompact)
    }

    /// Get compact view
    pub fn compact(self) -> Self {
        if self.is_create() {
            View::CreateCompact
        } else {
            View::Compact
        }
    }
}

/// Get attribute for inactive cards
pub fn inactive_attr(active: bool) -> &'static str {
    if active {
        ""
    } else {
        " inactive"
    }
}

/// Build a create card
fn html_card_create(res: Resource, create: &str) -> String {
    format!(
        "<div class='row'>\
          <span class='{TITLE}'>{res}</span>\
          <span class='{TITLE}'>Create</span>\
          <span class='{NAME}'>🆕</span>\
          {CLOSE_BUTTON}\
        </div>\
        {create}
        <div class='row end'>\
          {SAVE_BUTTON}\
        </div>"
    )
}

/// Build an edit card
fn html_card_edit(
    res: Resource,
    name: &str,
    edit: &str,
    delete: &'static str,
) -> String {
    let name = HtmlStr::new(name);
    format!(
        "<div class='row'>\
          <span class='{TITLE}'>{res}</span>\
          <span class='{TITLE}'>Edit</span>\
          <span class='{NAME}'>{name}</span>\
          {CLOSE_BUTTON}\
        </div>\
        {edit}\
        <div class='row'>\
          <span></span>\
          {delete}\
          {SAVE_BUTTON}\
        </div>"
    )
}
