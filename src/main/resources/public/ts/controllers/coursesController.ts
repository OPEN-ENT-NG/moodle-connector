import {ng, template} from 'entcore';
import {Course,Courses} from "../model";
import {Folder, Folders} from "../model/Folder";


export const mainController = ng.controller('MoodleController', ['$scope', 'route','$rootScope',($scope, route,$rootScope) => {
	$scope.lightboxes = {};
	$scope.params = {};
	$scope.currentTab='courses';
	$scope.printmenufolder=false;
    $scope.printmenucourseShared=false;
    $scope.currentfolderid=null;
    $scope.printcours=false;
    $scope.printfolders=false;
	$scope.courses= new Courses();
    $scope.folders=new Folders();

    $scope.isprintMenuFolder= function(){
        $scope.printmenufolder=!$scope.printmenufolder;
        $scope.printmenucourseShared=false;
        if($scope.printmenufolder){
            $scope.printfolders=true;
            $scope.printcours=true;
            $scope.currentfolderid=0;
            $scope.printCouresbySubFolder($scope.currentfolderid);
        }else{
            $scope.printfolders=false;
            $scope.printcours=false;
            $scope.currentfolderid=null;
        }
    }
    $scope.isprintMenuFolderShared= function(){
        $scope.printmenucourseShared=!$scope.printmenucourseShared;
        $scope.printmenufolder=false;
        $scope.currentfolderid=null;
        if($scope.printmenucourseShared){
            $scope.printfolders=false;
            $scope.printcours=true;
            $scope.initAllCouresbyuser();
        }else{
            $scope.printfolders=false;
            $scope.printcours=false;
        }
    }
    $scope.isprintSubFolder= function(folder:Folder){
       $scope.folders.all.forEach(function (e) {
           if(e.id==folder.id){
               e.printsubfolder=!e.printsubfolder;
               folder.printsubfolder=e.printsubfolder;
           }
       });
       if(folder.printsubfolder){
            $scope.currentfolderid=folder.id;
            $scope.printcours=true;
           $scope.printCouresbySubFolder(folder.id);
       }else{
           (folder.parent_id!=folder.id)? $scope.currentfolderid=folder.parent_id :$scope.currentfolderid=0;

           $scope.printCouresbySubFolder($scope.currentfolderid);
       }

    }

    $scope.printCouresbySubFolder= function(idfolder:number){
        $scope.initCouresbyFolder(idfolder);
        $scope.printcours=true;
        $scope.printfolders=true;

    }

	$scope.initCouresbyFolder = async function(idfolder:number){
        Promise.all([
            await $scope.courses.getCoursesbyFolder(idfolder)
        ]).then($scope.$apply());
    }
    $scope.initAllCouresbyuser = async function(){
        Promise.all([
            await $scope.courses.getCoursesAndSheredbyFolder()
        ]).then($scope.$apply());
    }

    $scope.initFolders = async function(){
        Promise.all([
            await $scope.folders.sync()
        ]).then($scope.$apply());
    }


    $scope.getFolderParent= function (): Folder[]{
        return $scope.folders.getparentFolder();
    }
    $scope.getSubFolder= function (folder:Folder): Folder[]{
        return $scope.folders.getSubFolder(folder.id);
    }

    $scope.countItems =async function (folder:Folder){
        Promise.all([
            await folder.countitems()
        ]).then( $scope.$apply());
    }
    $scope.switchTab= function(current: string){
        $scope.currentTab=current;
    }
	route({
		view: function(params){
			template.open('main', 'main');
		},
        dashboard: async () => {
            template.open('dashboard-main', 'page-dashboard');
            $scope.$apply();
        },
        mycourses: async () => {
        template.open('courses-main', 'page-courses');
        $scope.$apply();
        },
        library: async () => {
        template.open('library-main', 'page-library');
        $scope.$apply();
    }
	});
    $scope.openLightbox = false;

    /**
     * Create a course
     */
    $scope.openPopUp = function () {
        $scope.course = new Course();
        $scope.openLightbox = true;
    };

    $scope.openDeletePopUp = function () {
        $scope.course = new Course();
        $scope.course.delete();
    };

    $scope.closePopUp = function () {
        $scope.openLightbox = false;
    };

    $scope.createCourse = function() {
        $scope.course.create();
    };
}]);